package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * A `(from, to)` namespace-bound remap view over a [MappingTree] — the hot-path
 * API for the ASM/JST transform pipelines. Namespace validation and the
 * identity shortcut are paid once at construction rather than per lookup.
 *
 * Member names resolve by exact `(name, desc)` match; a member mapping without
 * a name in [to] keeps the original name. (Wildcards carry no names in this
 * model — they group params/lvars by descriptor, so they play no part in
 * member-name remapping.)
 */
interface NamespaceRemapper {

    val from: Namespace
    val to: Namespace

    /** Maps a class internal name; falls back to package mapping for unmapped classes. */
    fun mapClass(name: InternalName): InternalName

    /** Maps a field name within [owner]; desc disambiguates overloads. */
    fun mapField(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor?): UnqualifiedName

    /** Maps a method name within [owner]; desc disambiguates overloads. */
    fun mapMethod(owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor?): UnqualifiedName

    /**
     * Maps a method name within [owner] when the descriptor is only known by
     * prefix — for arbitrary-invokedynamic bsm names and other contexts
     * where the full desc isn't available.
     * [member]'s descriptor may be a desc prefix (e.g. `name;()`), not a
     * full [MethodDescriptor]. Resolves only on a unique match; default
     * keeps the source name.
     */
    fun mapMethodPrefixDesc(owner: InternalName, member: MethodNameAndDescriptor): UnqualifiedName = member.name

    /** Remaps every class reference inside an arbitrary [Type] (descriptors, signatures, etc.). */
    fun <T : Type> map(value: T): T

}

/** Binds [from] → [to] once; see [NamespaceRemapper]. */
fun MappingTree.remapper(from: Namespace, to: Namespace): NamespaceRemapper {
    checkNamespace(from)
    checkNamespace(to)
    return NamespaceRemapperImpl(this, from, to)
}

/** Post-processes mapped class names, replacing [separator] (`__` by
 *  default — feather-style Java-identifier markers for nested classes)
 *  with `$` so output jars carry JVM-conventional inner names and
 *  `InnerClasses` attributes/`InnerClasses`-aware tooling see real
 *  inner classes. Identity passthroughs are untouched — only names the
 *  tree actually remaps get the rewrite, so a legitimately-`__`-named
 *  unmapped class survives intact. */
fun NamespaceRemapper.dollarInnerClasses(separator: String = "__"): NamespaceRemapper {
    val delegate = this
    return object : NamespaceRemapper by delegate {
        override fun mapClass(name: InternalName): InternalName {
            val mapped = delegate.mapClass(name)
            if (mapped == name || !mapped.value.contains(separator)) {
                return mapped
            }
            return InternalName.read(mapped.value.replace(separator, "$"), true)
        }
    }
}

private class NamespaceRemapperImpl(
    private val tree: MappingTree,
    override val from: Namespace,
    override val to: Namespace,
) : NamespaceRemapper {

    private val identity = from == to

    override fun mapClass(name: InternalName): InternalName =
        if (identity) name else tree.map(from, to, name)

    override fun mapField(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor?): UnqualifiedName {
        if (identity) return name
        val cls = tree.getClass(from, owner) ?: return name
        return with(tree) { cls.getField(from, name, desc) }?.names[to] ?: name
    }

    override fun mapMethod(owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor?): UnqualifiedName {
        if (identity) return name
        val cls = tree.getClass(from, owner) ?: return name
        return with(tree) { cls.getMethod(from, name, desc) }?.names[to] ?: name
    }

    override fun mapMethodPrefixDesc(owner: InternalName, member: MethodNameAndDescriptor): UnqualifiedName {
        if (identity) return member.name
        val cls = tree.getClass(from, owner) ?: return member.name
        val descPrefix = member.descriptor?.value
        val match = cls.methods.singleOrNull { m ->
            val e = m.entries[from]
            e?.name == member.name && (descPrefix == null || e.descriptor?.value?.startsWith(descPrefix) == true)
        } ?: return member.name
        return match.names[to] ?: member.name
    }

    override fun <T : Type> map(value: T): T =
        if (identity) value else tree.map(from, to, value)

}
