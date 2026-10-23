package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper as nsBoundRemapper

/**
 * ASM [Remapper] backed by a [NamespaceRemapper] — every lookup resolves
 * `fns → targetNs` against the (propagated) [MappingTree].
 *
 * Descriptors, signatures, invokedynamic bootstrap names and constant values
 * all delegate through the base [Remapper] machinery (`mapDesc`, `mapType`,
 * `mapSignature`, `mapValue`), so only the name-level hooks are implemented
 * here. Member rows missing a `targetNs` name keep their source name — see
 * [NamespaceRemapper].
 */
class MappingRemapper(
    private val remapper: NamespaceRemapper,
) : Remapper() {

    constructor(
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
    ) : this(tree.nsBoundRemapper(fns, targetNs))

    override fun map(internalName: String): String =
        remapper.mapClass(InternalName.unchecked(internalName)).value

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        if (name == "<init>" || name == "<clinit>") return name
        return remapper.mapMethod(
            InternalName.unchecked(owner),
            UnqualifiedName.unchecked(name),
            MethodDescriptor.unchecked(descriptor),
        ).value
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String =
        remapper.mapField(
            InternalName.unchecked(owner),
            UnqualifiedName.unchecked(name),
            FieldDescriptor.unchecked(descriptor),
        ).value

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        val internal = InternalName.unchecked(owner)
        val unqualified = UnqualifiedName.unchecked(name)
        // components share their backing field's name; fall back to a name-only
        // match when the component desc doesn't line up with a field desc
        val exact = remapper.mapField(internal, unqualified, FieldDescriptor.unchecked(descriptor))
        return (if (exact != unqualified) exact else remapper.mapField(internal, unqualified, null)).value
    }

    override fun mapAnnotationAttributeName(descriptor: String, name: String): String =
        remapper.mapMethod(
            InternalName.unchecked(Type.getType(descriptor).internalName),
            UnqualifiedName.unchecked(name),
            null,
        ).value

}
