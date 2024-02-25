package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor

class ExceptionNode(parent: MethodNode, val type: ExceptionType, val exception: InternalName, val baseNs: Namespace) : BaseNode<ExceptionVisitor, MethodVisitor>(parent), ExceptionVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: MethodVisitor, nsFilter: List<Namespace>, minimize: Boolean): ExceptionVisitor? {
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

}