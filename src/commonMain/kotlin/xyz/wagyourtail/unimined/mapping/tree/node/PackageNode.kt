package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationType
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.PackageVisitor

class PackageNode(parent: MappingTree) : BaseNode<PackageVisitor, MappingVisitor>(parent), PackageVisitor {
    private val _names: MutableMap<Namespace, PackageName?> = mutableMapOf()
    private val _annotations: MutableSet<AnnotationNode<PackageVisitor>> = mutableSetOf()
    val names: Map<Namespace, PackageName?> get() = _names
    val annotations: Set<AnnotationNode<PackageVisitor>> get() = _annotations

    fun getName(namespace: Namespace) = names[namespace]

    fun setNames(names: Map<Namespace, PackageName?>) {
        root.mergeNs(names.keys)
        _names.putAll(names)
    }

    override fun acceptOuter(visitor: MappingVisitor, minimize: Boolean): PackageVisitor? {
        return visitor.visitPackage(names.filterNotNullValues())
    }

    override fun acceptInner(visitor: PackageVisitor, minimize: Boolean) {
        for (annotation in annotations) {
            annotation.accept(visitor, minimize)
        }
        super.acceptInner(visitor, minimize)
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
        return node
    }

}