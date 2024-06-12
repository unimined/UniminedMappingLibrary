package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationType
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationVisitor

class AnnotationNode<U: AnnotationParentVisitor<U>>(parent: BaseNode<U, *>, val type: AnnotationType, val baseNs: Namespace, val annotation: Annotation) : BaseNode<AnnotationVisitor, U>(parent), AnnotationVisitor {
    val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: U, minimize: Boolean): AnnotationVisitor? {
        return visitor.visitAnnotation(type, baseNs, annotation, namespaces.toSet())
    }
}