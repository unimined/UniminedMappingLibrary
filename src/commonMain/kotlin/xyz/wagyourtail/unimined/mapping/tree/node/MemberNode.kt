package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.visitor.*

abstract class MemberNode<T: MemberVisitor<T>, V: SignatureParentVisitor<V>, U: BaseVisitor<U>>(parent: BaseNode<U, *>) : AccessParentNode<T, U>(parent), MemberVisitor<T> {
    private var _signature: SignatureNode<V>? = null
    private var _comment: CommentNode<T>? = null
    private val _annotations: MutableSet<AnnotationNode<T>> = mutableSetOf()

    val signature: SignatureNode<V>? get() = _signature
    val comment: CommentNode<T>? get() = _comment
    val annotations: Set<AnnotationNode<T>> get() = _annotations

    override fun visitComment(values: Map<Namespace, String>): CommentVisitor? {
        if (_comment == null) {
            _comment = CommentNode(this)
        }
        _comment?.addComments(values)
        return _comment
    }

    /**
     * on this type so I don't have to implement it 3 times,
     * just don't call from param/lv
     */
    fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
        if (_signature == null) {
            _signature = SignatureNode(this as BaseNode<V, *>)
        }
        _signature?.setNames(values)
        return _signature
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

    override fun acceptInner(visitor: T, minimize: Boolean) {
        for (annotation in annotations) {
            annotation.accept(visitor, minimize)
        }
        signature?.accept(visitor as V, minimize)
        comment?.accept(visitor, minimize)
        super.acceptInner(visitor, minimize)
    }

}