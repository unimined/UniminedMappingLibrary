package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import xyz.wagyourtail.unimined.mapping.DelegatedNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.GraphNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
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
open class MappingRemapper(
    /** the underlying namespace resolver — inspectable (e.g. for
     *  graph-aware callers) and replaceable via subclassing the hooks */
    val remapper: NamespaceRemapper,
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
        // Remapper.mapValue can route field Handles through here — a
        // non-method desc means it's actually a field ref
        if (!descriptor.startsWith("(")) return mapFieldName(owner, name, descriptor)
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

    /**
     * The owner a field *instruction* should carry post-remap — the
     * mapped declaring class of the member the ref resolves to, not the
     * callsite owner. A subclass can declare a field whose name collides
     * with an inherited member's target name (FG2-era FML adds
     * `EntityPlayer.eyeHeight`; yarn names the inherited
     * `Entity.field_70129_M` `eyeHeight` too) — a callsite-owned ref
     * then binds statically to the child's field. Fields bind by
     * (owner, name, desc) at link time, so pinning the declaring class
     * preserves the binding exactly; method refs keep callsite owners
     * (their collisions need renames to fix, not owner rewrites).
     * Falls back to the mapped callsite owner when the underlying
     * remapper isn't graph-backed or can't resolve the member.
     */
    open fun mapFieldInsnOwner(owner: String, name: String, descriptor: String): String {
        var r = remapper
        while (r is DelegatedNamespaceRemapper) r = r.delegate
        val g = r as? GraphNamespaceRemapper ?: return map(owner)
        return g.fieldInsnOwner(
            InternalName.unchecked(owner),
            UnqualifiedName.unchecked(name),
            FieldDescriptor.unchecked(descriptor),
        ).value
    }

    /**
     * Field [Handle] constants (`ldc`/`invokedynamic` bsm args) bind by
     * (owner, name, desc) just like instructions — same declaring-owner
     * rewrite, so a shadowed member can't rebind through the callsite.
     */
    override fun mapValue(value: Any?): Any? {
        if (value is Handle && value.tag in Opcodes.H_GETFIELD..Opcodes.H_PUTSTATIC) {
            return Handle(
                value.tag,
                mapFieldInsnOwner(value.owner, value.name, value.desc),
                mapFieldName(value.owner, value.name, value.desc),
                mapDesc(value.desc),
                value.isInterface,
            )
        }
        return super.mapValue(value)
    }

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String {
        val internal = InternalName.unchecked(owner)
        val unqualified = UnqualifiedName.unchecked(name)
        // components share their backing field's name; fall back to a name-only
        // match when the component desc doesn't line up with a field desc
        val exact = remapper.mapField(internal, unqualified, FieldDescriptor.unchecked(descriptor))
        return (if (exact != unqualified) exact else remapper.mapField(internal, unqualified, null)).value
    }

    override fun mapAnnotationAttributeName(descriptor: String, name: String): String =
        mapAnnotationAttributeName(descriptor, MethodNameAndDescriptor.unchecked(name))

    /**
     * Attribute-desc-aware form via [MethodNameAndDescriptor] — attr names
     * are `()…` methods on the annotation type, so `name;()Z` exact-matches
     * [member]'s desc; a bare `name` (or `name;()`, i.e. the attribute type
     * couldn't be inferred) falls back to a `()`-prefix match.
     */
    open fun mapAnnotationAttributeName(descriptor: String, member: MethodNameAndDescriptor): String {
        val owner = InternalName.unchecked(Type.getType(descriptor).internalName)
        val desc = member.descriptor?.value
        return if (desc == null || desc == "()") {
            remapper.mapMethodPrefixDesc(owner, MethodNameAndDescriptor.unchecked("${member.name};()")).value
        } else {
            remapper.mapMethod(owner, member.name, member.descriptor).value
        }
    }

    /**
     * Invokedynamic callsite names that aren't `LambdaMetafactory` SAM names
     * pass through here — identity by default. Override for custom bsm
     * semantics; [mapMethodNamePrefixDesc] helps when the arg's full desc
     * isn't known.
     */
    open override fun mapInvokeDynamicMethodName(name: String, descriptor: String): String = name

    /**
     * Maps a method name when only a descriptor prefix is known — unique
     * match in [owner]'s hierarchy only.
     */
    fun mapMethodNamePrefixDesc(owner: String, name: String, descPrefix: String?): String =
        remapper.mapMethodPrefixDesc(
            InternalName.unchecked(owner),
            MethodNameAndDescriptor.unchecked(if (descPrefix != null) "$name;$descPrefix" else name),
        ).value

}
