package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.visitor.EmptyJavadocParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyJavadocVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocParentNode
import xyz.wagyourtail.unimined.mapping.visitor.JavadocVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateJavadocVisitor

class JavadocNode<T: JavadocParentNode<T>>(parent: BaseNode<T, *>, val value: String) : BaseNode<JavadocVisitor, T>(parent), JavadocVisitor {
    val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): JavadocVisitor? {
        return visitor.visitJavadoc(value, nsFilter.filter { it in namespaces }.toSet())
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitJavadoc(EmptyJavadocParentVisitor(), value, namespaces)
        if (inner) acceptInner(DelegateJavadocVisitor(EmptyJavadocVisitor(), delegator), root.namespaces, true)
    }

}