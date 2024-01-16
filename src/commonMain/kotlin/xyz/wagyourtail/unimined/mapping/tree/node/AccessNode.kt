package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MemberVisitor

class AccessNode<U: MemberVisitor<U>>(parent: MemberNode<U, *>, val accessType: AccessType, val accessFlag: AccessFlag) : BaseNode<AccessVisitor, U>(parent), AccessVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: U, minimize: Boolean) = visitor.visitAccess(accessType, accessFlag, namespaces)
}