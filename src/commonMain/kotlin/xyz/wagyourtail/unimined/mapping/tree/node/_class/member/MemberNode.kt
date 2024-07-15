package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.tree.node.*
import xyz.wagyourtail.unimined.mapping.visitor.*

abstract class MemberNode<T: MemberVisitor<T>, U: BaseVisitor<U>>(parent: BaseNode<U, *>) : AccessParentNode<T, U>(parent), MemberVisitor<T> {
    private var _comments: MutableSet<JavadocNode<T>> = mutableSetOf()
    private val _annotations: MutableSet<AnnotationNode<T>> = mutableSetOf()

    val comments: Set<JavadocNode<T>> get() = _comments
    val annotations: Set<AnnotationNode<T>> get() = _annotations

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        val node = JavadocNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _comments?.add(node)
        return node
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

    override fun acceptInner(visitor: T, nsFilter: Collection<Namespace>) {
        for (annotation in annotations) {
            annotation.accept(visitor, nsFilter)
        }
        for (comment in comments) {
            comment.accept(visitor, nsFilter)
        }
        super.acceptInner(visitor, nsFilter)
    }

}