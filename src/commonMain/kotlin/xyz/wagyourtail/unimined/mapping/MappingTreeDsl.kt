package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.element.metadata.ExceptionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.AnnotationElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.SignatureElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature

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
 *         javadoc("class doc", "named")
 *     }
 * }
 * ```
 */
fun mappingTree(
    vararg namespaces: String,
    block: MappingTreeDsl.() -> Unit = {}
): MutableMappingTree = MappingTreeDsl(namespaces.toList()).apply(block).tree

/**
 * Namespace-bound scope for [mappingTree]. Open so callers can extend it —
 * e.g. a Groovy-facing subclass adding `Closure` overloads — by
 * overriding the `*Scope` factories to return extended scope objects.
 */
@MappingTreeDslMarker
open class MappingTreeDsl(namespaces: List<String>) {

    /** Declared namespaces, in cell order. */
    val namespaces = namespaces.map { Namespace(it) }

    /** The tree under construction. */
    val tree = MutableMappingTree(this.namespaces)

    /** Positional `namespace -> parsed` map; `null` cells stay unmapped. */
    protected fun <T> row(
        vararg values: String?, parse: (String) -> T
    ): Map<Namespace, T> =
        this.namespaces.mapIndexedNotNull { i, ns ->
            values.getOrNull(i)?.let { ns to parse(it) }
        }.toMap()

    /** Resolve a declared namespace by name. */
    fun namespace(name: String): Namespace =
        namespaces.first { it.name == name }

    /** Member scope opened by [clazz]; override to return an extension. */
    protected open fun classScope(cls: MutableClassMapping): ClassDsl =
        ClassDsl(this, cls)

    /** Constant-group scope opened by [constantGroup]; override to extend. */
    protected open fun constantGroupScope(
        group: MutableConstantGroupMapping
    ): ConstantGroupDsl = ConstantGroupDsl(this, group)

    /** Leaf-metadata scope opened by the `block` on leaf rows. */
    protected open fun metaScope(parent: MutableMetadataParent): MetaDsl =
        MetaDsl(this, parent)

    /** Add a package mapping (`tiny`'s `p` row); [block] opens a [MetaDsl]. */
    open fun pkg(vararg names: String?, block: MetaDsl.() -> Unit = {}) =
        tree.package_(row(*names) { PackageName.unchecked(it) })
            .also { metaScope(it).block() }

    /** Add a class row. */
    open fun clazz(vararg names: String?): MutableClassMapping =
        clazz(*names) {}

    /** Add a class row; [block] opens a [ClassDsl] for members. */
    open fun clazz(vararg names: String?, block: ClassDsl.() -> Unit): MutableClassMapping {
        val cls = tree.clazz(row(*names) { InternalName.unchecked(it) })
        classScope(cls).block()
        return cls
    }

    /** Add a constant-group row (unpick-style `c` row data). */
    open fun constantGroup(
        type: InlineType,
        name: String? = null,
        baseNs: String
    ): MutableConstantGroupMapping = constantGroup(type, name, baseNs) {}

    /** Add a constant-group row; [block] opens a [ConstantGroupDsl] for
     *  `constant`/`target`/`expression` rows. */
    open fun constantGroup(
        type: InlineType,
        name: String? = null,
        baseNs: String,
        block: ConstantGroupDsl.() -> Unit
    ): MutableConstantGroupMapping {
        val group = tree.constantGroup(
            type, name, namespaces.first { it.name == baseNs }
        )
        constantGroupScope(group).block()
        return group
    }

    /** Member scope inside [clazz]. */
    @MappingTreeDslMarker
    open class ClassDsl(
        protected val treeDsl: MappingTreeDsl,
        /** The class row this scope writes members into. */
        val cls: MutableClassMapping
    ) : MetaScope {

        override val metaParent: MutableMetadataParent get() = cls

        override fun ns(name: String): Namespace = treeDsl.namespace(name)

        /** Member scope opened by [method]; override to return an extension. */
        protected open fun methodScope(method: MutableMethodMapping): MethodDsl =
            MethodDsl(treeDsl, method)

        /** Leaf-metadata scope opened by leaf-row `block`s. */
        protected open fun metaScope(parent: MutableMetadataParent): MetaDsl =
            treeDsl.metaScope(parent)

        /** Add a field row; entries are `name[;desc]` per namespace. */
        open fun field(vararg entries: String?, block: MetaDsl.() -> Unit = {}) =
            cls.field(treeDsl.row(*entries) { FieldNameAndDescriptor.unchecked(it) })
                .also { metaScope(it).block() }

        /** Add a method row; entries are `name[;desc]` per namespace. */
        open fun method(vararg entries: String?): MutableMethodMapping =
            method(*entries) {}

        /** Add a method row; [block] opens a [MethodDsl] for members. */
        open fun method(vararg entries: String?, block: MethodDsl.() -> Unit): MutableMethodMapping {
            val m = cls.method(treeDsl.row(*entries) { MethodNameAndDescriptor.unchecked(it) })
            methodScope(m).block()
            return m
        }

        /** Add a wildcard row; entries are `desc` or `name desc` per namespace. */
        open fun wildcard(type: WildcardType, vararg descs: String?, block: MetaDsl.() -> Unit = {}) =
            cls.wildcard(type, treeDsl.row(*descs) { FieldOrMethodDescriptor.unchecked(it) })
                .also { metaScope(it).block() }

        /** Add an `inject-interface` directive (class-tweaker). */
        fun interface_(signature: String, baseNs: String, type: AddRemove = AddRemove.ADD) =
            metadata(InterfaceElement(type, ClassTypeSignature.unchecked(signature), ns(baseNs)))

        /** Add an `extend-enum` directive (class-tweaker). */
        fun enumExtension(name: String, baseNs: String, type: AddRemove = AddRemove.ADD) =
            metadata(EnumExtensionElement(type, UnqualifiedName.unchecked(name), ns(baseNs)))
    }

    /** Parameter/local-variable scope inside [ClassDsl.method]. */
    @MappingTreeDslMarker
    open class MethodDsl(
        protected val treeDsl: MappingTreeDsl,
        /** The method row this scope writes members into. */
        val method: MutableMethodMapping
    ) : MetaScope {

        override val metaParent: MutableMetadataParent get() = method

        override fun ns(name: String): Namespace = treeDsl.namespace(name)

        /** Leaf-metadata scope opened by leaf-row `block`s. */
        protected open fun metaScope(parent: MutableMetadataParent): MetaDsl =
            treeDsl.metaScope(parent)

        /** Add a parameter row (`p` in tiny) — merges on
         *  `index`/`lvOrd` identity; [block] opens a [MetaDsl]. */
        open fun parameter(
            index: Int? = null,
            lvOrd: Int? = null,
            vararg names: String?,
            block: MetaDsl.() -> Unit = {}
        ) = method.parameter(
            index, lvOrd, treeDsl.row(*names) { UnqualifiedName.unchecked(it) }
        ).also { metaScope(it).block() }

        /** Add a local-variable row (`v` in tiny); [block] opens a [MetaDsl]. */
        open fun localVariable(
            lvOrd: Int,
            startOp: Int? = null,
            vararg names: String?,
            block: MetaDsl.() -> Unit = {}
        ) = method.localVariable(
            lvOrd, startOp, treeDsl.row(*names) { UnqualifiedName.unchecked(it) }
        ).also { metaScope(it).block() }

        /** Add a `throws` directive (class-tweaker `exception`). */
        fun exception(exception: String, baseNs: String, type: AddRemove = AddRemove.ADD) =
            metadata(ExceptionElement(type, InternalName.unchecked(exception), ns(baseNs)))
    }

    /** Constant/target/expression scope inside [constantGroup]. */
    @MappingTreeDslMarker
    open class ConstantGroupDsl(
        protected val treeDsl: MappingTreeDsl,
        /** The group row this scope writes constants into. */
        val group: MutableConstantGroupMapping
    ) : MetaScope {

        override val metaParent: MutableMetadataParent get() = group

        override fun ns(name: String): Namespace = treeDsl.namespace(name)

        /** Add a constant — `fieldClass.name` optionally with `desc`. */
        open fun constant(fieldClass: String, fieldName: String, fieldDesc: String? = null) =
            group.constant(
                InternalName.unchecked(fieldClass),
                UnqualifiedName.unchecked(fieldName),
                fieldDesc?.let { FieldDescriptor.unchecked(it) }
            )

        /** Add a target — `Owner.name` optionally with desc, at [paramIdx]. */
        open fun target(qualifiedName: String? = null, paramIdx: Int? = null) =
            group.target(qualifiedName?.let { FullyQualifiedName.unchecked(it) }, paramIdx)

        /** Add an unpick-style `constant = expression` row. */
        open fun expression(constant: String, expression: String) =
            group.expression(Constant.unchecked(constant), Expression.unchecked(expression))
    }

    /** Metadata scope opened by leaf-row `block`s — attach metadata
     *  (`metadata`, `javadoc`, `signature`, `annotation`) to a row. */
    @MappingTreeDslMarker
    open class MetaDsl(
        protected val treeDsl: MappingTreeDsl,
        override val metaParent: MutableMetadataParent
    ) : MetaScope {

        override fun ns(name: String): Namespace = treeDsl.namespace(name)
    }

    /** Shared metadata sugar — available on every scoped row
     *  (class/method/constant-group scopes and leaf-row blocks).
     *  Interface methods, not extensions, so Groovy-facing subclasses
     *  can expose them to `Closure` delegates. */
    interface MetaScope {

        /** The row metadata attaches to. */
        val metaParent: MutableMetadataParent

        /** Resolve a declared namespace by name (for `baseNs` args). */
        fun ns(name: String): Namespace

        /** Attach arbitrary [MetadataElement]s (access rules, inner-class,
         *  seal, synthetic-bridge, constant-attribute, …). */
        fun metadata(vararg elements: MetadataElement) =
            elements.forEach(metaParent::addMetadata)

        /** Attach a javadoc/comment to the row. */
        fun javadoc(text: String, baseNs: String) =
            metadata(JavadocElement(text, ns(baseNs)))

        /** Attach a JVMS class signature to the row. */
        fun signature(signature: String, baseNs: String) =
            metadata(
                SignatureElement(
                    runCatching { JVMS.parseClassSignature(signature) }
                        .getOrElse { ClassSignature.unchecked(signature) },
                    ns(baseNs)
                )
            )

        /** Attach an annotation (class-tweaker `annotation` row). */
        fun annotation(
            annotation: String,
            baseNs: String,
            type: AddRemoveModify = AddRemoveModify.ADD
        ) = metadata(
            AnnotationElement(type, ns(baseNs), Annotation.unchecked(annotation))
        )

    }

}

@DslMarker
annotation class MappingTreeDslMarker
