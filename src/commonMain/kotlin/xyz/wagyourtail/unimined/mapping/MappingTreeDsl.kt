package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * Concise builder for [MutableMappingTree] test fixtures and stub
 * mappings — cell values are positional across the declared namespace
 * order; a `null` cell leaves that namespace unmapped.
 *
 * ```kotlin
 * val tree = mappingTree("official", "named") {
 *     pkg("a/", "b/")
 *     clazz("a/A", "b/B") {
 *         field("f;I", "g")
 *         method("m;()V", "n") {
 *             parameter(0, null, null, "arg")
 *         }
 *     }
 * }
 * ```
 */
fun mappingTree(
    vararg namespaces: String,
    block: MappingTreeDsl.() -> Unit = {}
): MutableMappingTree = MappingTreeDsl(namespaces.toList()).apply(block).tree

/** Namespace-bound scope for [mappingTree]. */
@MappingTreeDslMarker
class MappingTreeDsl internal constructor(namespaces: List<String>) {

    internal val namespaces = namespaces.map { Namespace(it) }
    internal val tree = MutableMappingTree(this.namespaces)

    internal fun <T> row(vararg values: String?, parse: (String) -> T): Map<Namespace, T> =
        this.namespaces.mapIndexedNotNull { i, ns ->
            values.getOrNull(i)?.let { ns to parse(it) }
        }.toMap()

    /** Add a package mapping (`tiny`'s `p` row). */
    fun pkg(vararg names: String?) =
        tree.package_(row(*names) { PackageName.unchecked(it) })

    /** Add a class row; [block] opens a [ClassDsl] for members. */
    fun clazz(vararg names: String?, block: ClassDsl.() -> Unit = {}): MutableClassMapping {
        val cls = tree.clazz(row(*names) { InternalName.unchecked(it) })
        ClassDsl(this).apply { this.cls = cls }.block()
        return cls
    }

    /** Add a constant-group row (unpick-style `c` row data). */
    fun constantGroup(type: InlineType, name: String? = null, baseNs: String): MutableConstantGroupMapping =
        tree.constantGroup(type, name, namespaces.first { it.name == baseNs })

    /** Member scope inside [clazz]. */
    @MappingTreeDslMarker
    class ClassDsl internal constructor(private val treeDsl: MappingTreeDsl) {
        internal lateinit var cls: MutableClassMapping

        /** Add a field row; entries are `name[;desc]` per namespace. */
        fun field(vararg entries: String?) =
            cls.field(treeDsl.row(*entries) { FieldNameAndDescriptor.unchecked(it) })

        /** Add a method row; entries are `name[;desc]` per namespace. */
        fun method(vararg entries: String?, block: MethodDsl.() -> Unit = {}): MutableMethodMapping {
            val m = cls.method(treeDsl.row(*entries) { MethodNameAndDescriptor.unchecked(it) })
            MethodDsl(treeDsl).apply { method = m }.block()
            return m
        }

        /** Add a wildcard row; entries are `desc` or `name desc` per namespace. */
        fun wildcard(type: WildcardType, vararg descs: String?) =
            cls.wildcard(type, treeDsl.row(*descs) { FieldOrMethodDescriptor.unchecked(it) })
    }

    /** Parameter/local-variable scope inside [ClassDsl.method]. */
    @MappingTreeDslMarker
    class MethodDsl internal constructor(private val treeDsl: MappingTreeDsl) {
        internal lateinit var method: MutableMethodMapping

        /** Add a parameter row (`p` in tiny). */
        fun parameter(index: Int? = null, lvOrd: Int? = null, vararg names: String?) =
            method.parameter(index, lvOrd, treeDsl.row(*names) { UnqualifiedName.unchecked(it) })

        /** Add a local-variable row (`v` in tiny). */
        fun localVariable(lvOrd: Int, startOp: Int? = null, vararg names: String?) =
            method.localVariable(lvOrd, startOp, treeDsl.row(*names) { UnqualifiedName.unchecked(it) })
    }

}

@DslMarker
annotation class MappingTreeDslMarker
