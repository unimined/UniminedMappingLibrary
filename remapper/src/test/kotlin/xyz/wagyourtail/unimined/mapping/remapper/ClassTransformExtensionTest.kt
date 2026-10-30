package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.ext.classtransform.ClassTransformExtension
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.voidMethod
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ClassTransformExtension]: classtransform annotation values remap in place
 * and a JarModAgent `mappings` refmap is emitted.
 */
class ClassTransformExtensionTest {

    private fun tree() = treeOf().apply {
        cls("a/b", "pkg/Target").apply {
            method("a;()V", "foo;()V")
            field("a;I", "baz;I")
        }
    }

    /** `mod/Transformer` — `@CTransformer(pkg/Target)` + `@CInject`/`@CTarget`/`@CShadow` */
    private fun transformerBytes() = classBytes("mod/Transformer") {
        val cta = visitAnnotation("Lnet/lenni0451/classtransform/annotations/CTransformer;", true) as AnnotationNode
        cta.values = listOf("value", listOf(Type.getObjectType("pkg/Target")))
        methods.add(voidMethod("injectFoo").apply {
            val inject = visitAnnotation("Lnet/lenni0451/classtransform/annotations/injection/CInject;", true) as AnnotationNode
            inject.values = listOf(
                "method", "foo()V",
                "target", AnnotationNode("Lnet/lenni0451/classtransform/annotations/CTarget;").apply {
                    values = listOf("value", "INVOKE", "target", "Lpkg/Target;foo()V")
                },
            )
        })
        fields.add(org.objectweb.asm.tree.FieldNode(
            org.objectweb.asm.Opcodes.ACC_PUBLIC, "baz", "I", null, null,
        ).apply {
            visitAnnotation("Lnet/lenni0451/classtransform/annotations/CShadow;", true)
        })
    }

    private fun remap(): java.nio.file.Path {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")
        writeJar(input, mapOf("mod/Transformer.class" to transformerBytes()))
        AsmRemapper.remap(input, out, tree(), named, obf, Options(
            extensions = BuiltinAsmExtensions.defaults() +
                AsmExtensionFactory { ctx -> ClassTransformExtension(ctx) },
        ))
        return out
    }

    @Test
    fun directPostApply() {
        // bypass the jar pipeline: drive postApply on a pre-remapped node
        val tree = tree()
        val ctx = xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext(tree, named, obf)
        val input = org.objectweb.asm.tree.ClassNode().also {
            org.objectweb.asm.ClassReader(transformerBytes()).accept(it, 0)
        }
        val output = org.objectweb.asm.tree.ClassNode()
        input.accept(object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, output) {
            override fun visitAnnotation(d: String, v: Boolean) =
                object : org.objectweb.asm.AnnotationVisitor(org.objectweb.asm.Opcodes.ASM9, super.visitAnnotation(d, v)) {
                    override fun visit(name: String?, value: Any?) =
                        super.visit(name, if (value is Type) Type.getObjectType("a/b") else value)
                }
        })
        val ext = ClassTransformExtension(ctx)
        ext.postApply(input, output, null)
        val inject = output.methods.single { it.name == "injectFoo" }.visibleAnnotations.single()
        val vals = inject.values
        assertEquals("a()V", vals[vals.indexOf("method") + 1])
        assertEquals("a", output.fields.single().name)
    }

    @Test
    fun injectionValuesRemapInPlace() {
        val out = remap()
        val transformer = readClasses(out).getValue("mod/Transformer")
        val classAnn = transformer.visibleAnnotations.single()
        val targets = classAnn.values[classAnn.values.indexOf("value") + 1] as List<*>
        assertEquals("a/b", (targets.single() as Type).internalName)
        val inject = transformer.methods.single { it.name == "injectFoo" }.visibleAnnotations.single()
        val vals = inject.values
        assertEquals("a()V", vals[vals.indexOf("method") + 1])
        val ctarget = vals[vals.indexOf("target") + 1] as AnnotationNode
        assertEquals("La/b;a()V", ctarget.values[ctarget.values.indexOf("target") + 1])
    }

    @Test
    fun shadowMemberRenames() {
        val out = remap()
        val transformer = readClasses(out).getValue("mod/Transformer")
        assertTrue(transformer.fields.single().name == "a")
    }

    /** REFMAP mode: verbatim substitution — a source-space refmap shipped in
     *  the jar replaces annotation strings by dict lookup (mirrors JMA's
     *  RefmapAnnotationPreprocessor) */
    @Test
    fun refmapApplyMode() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")
        val refmap = """{"mappings":{"mod/Transformer":{"foo()V":"Lpkg/Target;foo()V","baz":"Lpkg/Target;baz:I"}}}"""
        writeJar(input, mapOf(
            "mod/Transformer.class" to transformerBytes(),
            "jarmodagent.refmap.json" to refmap.toByteArray(),
        ))
        AsmRemapper.remap(input, out, tree(), named, obf, Options(
            extensions = BuiltinAsmExtensions.defaults() +
                AsmExtensionFactory { ctx ->
                    ClassTransformExtension(ctx, mode = xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationApplyMode.REFMAP)
                },
        ))
        val transformer = readClasses(out).getValue("mod/Transformer")
        val inject = transformer.methods.single { it.name == "injectFoo" }.visibleAnnotations.single()
        val vals = inject.values
        // "foo()V" matched a refmap key → replaced by its remapped canonical value
        assertEquals("La/b;a()V", vals[vals.indexOf("method") + 1])
        // member names are NOT touched in verbatim mode
        assertEquals("baz", transformer.fields.single().name)
    }

    @Test
    fun refmapEmitted() {
        val out = remap()
        assertTrue("jarmodagent.refmap.json" in readEntries(out))
        ZipFile(out.toFile()).use { zin ->
            val json = Json.parseToJsonElement(
                zin.getInputStream(zin.getEntry("jarmodagent.refmap.json")).readBytes().decodeToString(),
            ).jsonObject
            val transformer = json.getValue("mappings").jsonObject.getValue("mod/Transformer").jsonObject
            assertEquals("La/b;a()V", transformer.getValue("foo()V").jsonPrimitive.content)
            assertEquals("La/b;a()V", transformer.getValue("Lpkg/Target;foo()V").jsonPrimitive.content)
        }
    }
}
