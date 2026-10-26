package xyz.wagyourtail.unimined.mapping.remapper.ext.metadata

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension
import xyz.wagyourtail.unimined.mapping.remapper.asm.MappingRemapper
import kotlin.metadata.*
import kotlin.metadata.jvm.*

/**
 * Remaps `kotlin.Metadata` annotations — the serialized kotlin declaration
 * data inside every kotlin-compiled class. Without this, kotlin-reflect and
 * multiplatform consumers see source-space names for classes/functions/
 * properties whose bytecode names were remapped.
 *
 * Decodes via `kotlinx-metadata` (`KotlinClassMetadata`), remaps the `Km*`
 * model in place — classifiers, member names, JVM signatures — and re-encodes.
 * Lenient reading tolerates metadata newer than the library.
 */
class KotlinMetadataExtension(
    private val ctx: ExtensionContext,
) : AsmExtension {

    private val asm by lazy { MappingRemapper(ctx.remapper) }

    override fun postApply(input: ClassNode, output: ClassNode, cls: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
        val ann = output.visibleAnnotations?.firstOrNull { it.desc == "Lkotlin/Metadata;" } ?: return
        val md = readMetadata(ann) ?: return
        // strict reads produce writable models (lenient ones don't); the read
        // version may exceed the write ceiling, so normalize before write()
        val km = try { KotlinClassMetadata.readStrict(md) } catch (e: Exception) { return }
        km.version = kotlin.metadata.jvm.JvmMetadataVersion.LATEST_STABLE_SUPPORTED
        val out = try { when (km) {
            is KotlinClassMetadata.Class -> { remapClass(km.kmClass); km.write() }
            is KotlinClassMetadata.FileFacade -> { remapPackage(km.kmPackage); km.write() }
            is KotlinClassMetadata.MultiFileClassPart -> {
                remapPackage(km.kmPackage)
                km.facadeClassName?.let { km.facadeClassName = ctx.mapClass(it) }
                km.write()
            }
            is KotlinClassMetadata.MultiFileClassFacade -> {
                km.partClassNames = km.partClassNames.map { ctx.mapClass(it) }
                km.write()
            }
            is KotlinClassMetadata.SyntheticClass -> {
                km.kmLambda?.let { remapFunction(it.function, output.name) }
                km.write()
            }
            else -> return
        } } catch (e: Exception) { return }  // partial/unset model — leave annotation untouched
        writeMetadata(ann, out)
    }

    private fun remapClass(km: KmClass) {
        // capture the source-space name for member lookups before it moves
        // to the target namespace
        val owner = km.name
        km.name = ctx.mapClass(km.name)
        km.supertypes.forEach { remapType(it) }
        km.nestedClasses.replaceAll { ctx.mapClass(it) }
        km.sealedSubclasses.replaceAll { ctx.mapClass(it) }
        km.enumEntries.replaceAll { ctx.mapField(owner, it, null) }
        km.typeParameters.forEach { tp -> tp.upperBounds.forEach { remapType(it) } }
        km.constructors.forEach { remapConstructor(it, owner) }
        km.functions.forEach { remapFunction(it, owner) }
        km.properties.forEach { remapProperty(it, owner) }
        km.typeAliases.forEach { ta ->
            ta.underlyingType?.let { remapType(it) }
            ta.expandedType?.let { remapType(it) }
        }
        km.companionObject?.let { km.companionObject = ctx.mapField(owner, it, null) }
    }

    private fun remapPackage(km: KmPackage) {
        km.functions.forEach { remapFunction(it, null) }
        km.properties.forEach { remapProperty(it, null) }
        km.typeAliases.forEach { ta ->
            ta.underlyingType?.let { remapType(it) }
            ta.expandedType?.let { remapType(it) }
        }
    }

    private fun remapConstructor(c: KmConstructor, owner: String) {
        c.valueParameters.forEach { remapType(it.type) }
        c.signature?.let { c.signature = JvmMethodSignature("<init>", asm.mapMethodDesc(it.descriptor)) }
    }

    private fun remapFunction(f: KmFunction, owner: String?) {
        val sig = f.signature
        val mapped = ctx.mapMethod(owner ?: "", f.name, sig?.descriptor ?: "*")
        f.name = mapped
        if (sig != null) f.signature = JvmMethodSignature(mapped, asm.mapMethodDesc(sig.descriptor))
        runCatching { f.returnType?.let(::remapType) }
        runCatching { f.receiverParameterType?.let(::remapType) }
        f.valueParameters.forEach { remapType(it.type) }
        f.typeParameters.forEach { tp -> tp.upperBounds.forEach { remapType(it) } }
    }

    private fun remapProperty(p: KmProperty, owner: String?) {
        val o = owner ?: ""
        p.name = ctx.mapField(o, p.name, p.fieldSignature?.descriptor ?: "*")
        p.fieldSignature?.let { p.fieldSignature = JvmFieldSignature(ctx.mapField(o, it.name, it.descriptor), asm.mapDesc(it.descriptor)) }
        p.getterSignature?.let { p.getterSignature = JvmMethodSignature(ctx.mapMethod(o, it.name, it.descriptor), asm.mapMethodDesc(it.descriptor)) }
        p.setterSignature?.let { p.setterSignature = JvmMethodSignature(ctx.mapMethod(o, it.name, it.descriptor), asm.mapMethodDesc(it.descriptor)) }
        runCatching { p.returnType?.let(::remapType) }
        runCatching { p.receiverParameterType?.let(::remapType) }
        p.typeParameters.forEach { tp -> tp.upperBounds.forEach { remapType(it) } }
    }

    private fun remapType(t: KmType) {
        val c = t.classifier
        t.classifier = when (c) {
            is KmClassifier.Class -> KmClassifier.Class(ctx.mapClass(c.name))
            is KmClassifier.TypeAlias -> KmClassifier.TypeAlias(ctx.mapClass(c.name))
            else -> c
        }
        t.arguments.forEach { it.type?.let(::remapType) }
        t.outerType?.let(::remapType)
        t.abbreviatedType?.let(::remapType)
        t.flexibleTypeUpperBound?.type?.let(::remapType)
    }

    /** extract the `kotlin.Metadata` annotation payload into an annotation instance */
    private fun readMetadata(ann: AnnotationNode): kotlin.Metadata? {
        var k = 1; var mv = intArrayOf(); var bv = intArrayOf(); var xi = 0
        var d1 = emptyArray<String>(); var d2 = emptyArray<String>(); var xs = ""; var pn = ""
        val v = ann.values ?: return null
        var i = 0
        while (i + 1 < v.size) {
            when (v[i]) {
                "k" -> k = v[i + 1] as Int
                "mv" -> mv = (v[i + 1] as List<*>).map { it as Int }.toIntArray()
                "bv" -> bv = (v[i + 1] as List<*>).map { it as Int }.toIntArray()
                "xi" -> xi = v[i + 1] as Int
                "d1" -> @Suppress("UNCHECKED_CAST") run { d1 = (v[i + 1] as List<String>).toTypedArray() }
                "d2" -> @Suppress("UNCHECKED_CAST") run { d2 = (v[i + 1] as List<String>).toTypedArray() }
                "xs" -> xs = v[i + 1] as String
                "pn" -> pn = v[i + 1] as String
            }
            i += 2
        }
        return kotlin.Metadata(k, mv, bv, d1, d2, xs, pn, xi)
    }

    /** write a remapped `kotlin.Metadata` back into the annotation node's values */
    private fun writeMetadata(ann: AnnotationNode, md: kotlin.Metadata) {
        val v = ann.values ?: return
        var i = 0
        while (i + 1 < v.size) {
            v[i + 1] = when (v[i]) {
                "mv" -> md.metadataVersion.toList()
                "bv" -> md.bytecodeVersion.toList()
                "d1" -> md.data1.toList()
                "d2" -> md.data2.toList()
                "xs" -> md.extraString
                "pn" -> md.packageName
                "xi" -> md.extraInt
                else -> v[i + 1]
            }
            i += 2
        }
    }
}
