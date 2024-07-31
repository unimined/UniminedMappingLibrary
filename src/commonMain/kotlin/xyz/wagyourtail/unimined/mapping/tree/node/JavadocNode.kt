package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.visitor.EmptyJavadocParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocParentNode
import xyz.wagyourtail.unimined.mapping.visitor.JavadocVisitor

class JavadocNode<T: JavadocParentNode<T>>(parent: BaseNode<T, *>, val value: String, val baseNs: Namespace) : BaseNode<JavadocVisitor, T>(parent), JavadocVisitor {
    val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): JavadocVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            return visitor.visitJavadoc(value, first, ns - first)
        } else {
            return visitor.visitJavadoc(value, baseNs, nsFilter.filter { it in namespaces }.toSet() - baseNs)
        }
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitJavadoc(EmptyJavadocParentVisitor(), value, baseNs, namespaces)
//        acceptInner(DelegateCommentVisitor(EmptyCommentVisitor(), delegator), root.namespaces)
    }

}