package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.tree.node.AccessParentNode
import xyz.wagyourtail.unimined.mapping.tree.node.AnnotationNode
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.JavadocNode
import xyz.wagyourtail.unimined.mapping.visitor.*

abstract class MemberNode<T: MemberVisitor<T>, U: BaseVisitor<U>>(parent: BaseNode<U, *>) : AccessParentNode<T, U>(parent), MemberVisitor<T> {
    private var _comments: MutableMap<String, JavadocNode<T>> = mutableMapOf()
    private val _annotations: MutableSet<AnnotationNode<T>> = mutableSetOf()

    val comments: Set<JavadocNode<T>> get() = _comments.values.toSet()
    val annotations: Set<AnnotationNode<T>> get() = _annotations

    override fun visitJavadoc(value: String, namespaces: Set<Namespace>): JavadocVisitor? {
        val node = _comments.getOrPut(value) { JavadocNode(this, value) }
        node.addNamespaces(namespaces)
        root.mergeNs(namespaces)
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