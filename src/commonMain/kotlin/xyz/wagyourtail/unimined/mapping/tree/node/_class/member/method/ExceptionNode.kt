package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.EmptyExceptionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateExceptionVisitor

class ExceptionNode<T: InvokableVisitor<T>>(parent: BaseNode<T, *>, val type: ExceptionType, val exception: InternalName, val baseNs: Namespace) : BaseNode<ExceptionVisitor, T>(parent), ExceptionVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): ExceptionVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val mapped = root.map(baseNs, first, exception)
            return visitor.visitException(type, mapped, first, ns - first)
        } else {
            return visitor.visitException(type, exception, baseNs, nsFilter.filter { it in namespaces }.toSet() - baseNs)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitException(EmptyMethodVisitor(), type, exception, baseNs, namespaces)
        if (inner) acceptInner(DelegateExceptionVisitor(EmptyExceptionVisitor(), delegator), root.namespaces, true)
    }

}