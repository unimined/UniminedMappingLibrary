package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.*

class PackageNode(parent: AbstractMappingTree) : BaseNode<PackageVisitor, MappingVisitor>(parent), PackageVisitor {
    private val _names: MutableMap<Namespace, PackageName?> = mutableMapOf()
    private val _annotations: MutableSet<AnnotationNode<PackageVisitor>> = mutableSetOf()
    private val _comments: MutableMap<Namespace, String?> = mutableMapOf()

    val names: Map<Namespace, PackageName?> get() = _names
    val annotations: Set<AnnotationNode<PackageVisitor>> get() = _annotations
    val comments: Map<Namespace, String?> get() = _comments

    fun getName(namespace: Namespace) = names[namespace]

    fun setNames(names: Map<Namespace, PackageName?>) {
        root.mergeNs(names.keys)
        _names.putAll(names)
    }

    override fun acceptOuter(visitor: MappingVisitor, nsFilter: Collection<Namespace>, minimize: Boolean): PackageVisitor? {
        val names = names.filterNotNullValues().filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitPackage(names)
    }

    override fun acceptInner(visitor: PackageVisitor, nsFilter: Collection<Namespace>, minimize: Boolean) {
        for (annotation in annotations) {
            annotation.accept(visitor, nsFilter, minimize)
        }
        super.acceptInner(visitor, nsFilter, minimize)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        val node = AnnotationNode(this, type, baseNs, annotation)
        node.addNamespaces(namespaces)
        _annotations.add(node)
        root.mergeNs(namespaces + baseNs)
        return node
    }

    override fun visitComment(values: Map<Namespace, String>): CommentVisitor? {
        root.mergeNs(values.keys)
        _comments.putAll(values)
        return null
    }

}