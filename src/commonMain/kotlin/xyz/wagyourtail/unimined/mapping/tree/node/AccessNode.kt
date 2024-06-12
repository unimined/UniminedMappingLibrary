package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.*

class AccessNode<U: AccessParentVisitor<U>>(parent: BaseNode<U, *>, val accessType: AccessType, val accessFlag: AccessFlag, val conditions: AccessConditions) : BaseNode<AccessVisitor, U>(parent), AccessVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: U, minimize: Boolean): AccessVisitor? {
        return visitor.visitAccess(accessType, accessFlag, conditions, namespaces)
    }

}