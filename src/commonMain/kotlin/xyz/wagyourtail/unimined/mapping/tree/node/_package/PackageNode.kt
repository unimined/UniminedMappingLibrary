package xyz.wagyourtail.unimined.mapping.tree.node._package

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.AnnotationNode
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.JavadocNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegatePackageVisitor

class PackageNode(parent: AbstractMappingTree) : BaseNode<PackageVisitor, MappingVisitor>(parent), PackageVisitor {
    private val _names: MutableMap<Namespace, PackageName> = mutableMapOf()
    private val _annotations: MutableSet<AnnotationNode<PackageVisitor>> = mutableSetOf()
    private var _comments: MutableMap<String, JavadocNode<PackageVisitor>> = mutableMapOf()

    val names: Map<Namespace, PackageName> get() = _names
    val annotations: Set<AnnotationNode<PackageVisitor>> get() = _annotations
    val comments: Set<JavadocNode<PackageVisitor>> get() = _comments.values.toSet()

    fun getName(namespace: Namespace) = names[namespace]

    fun setNames(names: Map<Namespace, PackageName>) {
        root.mergeNs(names.keys)
        _names.putAll(names)
    }

    override fun acceptOuter(visitor: MappingVisitor, nsFilter: Collection<Namespace>): PackageVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitPackage(names)
    }

    override fun acceptInner(visitor: PackageVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        for (annotation in if (sort) annotations.sortedBy { it.toString() } else annotations) {
            annotation.accept(visitor, nsFilter, sort)
        }
        super.acceptInner(visitor, nsFilter, sort)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor {
        val node = AnnotationNode(this, type, baseNs, annotation)
        node.addNamespaces(namespaces)
        _annotations.add(node)
        root.mergeNs(namespaces + baseNs)
        return node
    }

    override fun visitJavadoc(value: String, namespaces: Set<Namespace>): JavadocVisitor {
        val node = _comments.getOrPut(value) { JavadocNode(this, value) }
        node.addNamespaces(namespaces)
        root.mergeNs(namespaces)
        return node
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitPackage(EmptyMappingVisitor(), names)
        if (inner) acceptInner(DelegatePackageVisitor(EmptyPackageVisitor(), delegator), root.namespaces, true)
    }

}