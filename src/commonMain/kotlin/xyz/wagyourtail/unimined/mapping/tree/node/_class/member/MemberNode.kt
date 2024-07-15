package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.tree.node.*
import xyz.wagyourtail.unimined.mapping.visitor.*

abstract class MemberNode<T: MemberVisitor<T>, V: SignatureParentVisitor<V>, U: BaseVisitor<U>>(parent: BaseNode<U, *>) : AccessParentNode<T, U>(parent), MemberVisitor<T> {
    private var _signature: SignatureNode<V>? = null
    private var _comments: MutableSet<JavadocNode<T>> = mutableSetOf()
    private val _annotations: MutableSet<AnnotationNode<T>> = mutableSetOf()

    val signature: SignatureNode<V>? get() = _signature
    val comments: Set<JavadocNode<T>> get() = _comments
    val annotations: Set<AnnotationNode<T>> get() = _annotations

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        val node = JavadocNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _comments?.add(node)
        return node
    }

    /**
     * on this type so I don't have to implement it 3 times,
     * just don't call from param/lv
     */
    fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
        if (_signature == null) {
            @Suppress("UNCHECKED_CAST")
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

    override fun acceptInner(visitor: T, nsFilter: Collection<Namespace>) {
        @Suppress("UNCHECKED_CAST")
        signature?.accept(visitor as V, nsFilter)
        for (annotation in annotations) {
            annotation.accept(visitor, nsFilter)
        }
        for (comment in comments) {
            comment.accept(visitor, nsFilter)
        }
        super.acceptInner(visitor, nsFilter)
    }

}