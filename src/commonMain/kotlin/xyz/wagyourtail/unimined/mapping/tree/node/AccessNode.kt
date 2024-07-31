package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAccessParentVisitor

class AccessNode<U: AccessParentVisitor<U>>(parent: BaseNode<U, *>, val accessType: AccessType, val accessFlag: AccessFlag, val conditions: AccessConditions) : BaseNode<AccessVisitor, U>(parent), AccessVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): AccessVisitor? {
        val ns = nsFilter.filter { it in namespaces }.toSet()
        if (ns.isEmpty()) return null
        return visitor.visitAccess(accessType, accessFlag, conditions, ns)
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitAccess(EmptyAccessParentVisitor(), accessType, accessFlag, conditions, namespaces)
//        acceptInner(DelegateAccessVisitor(EmptyAccessVisitor(), delegator), root.namespaces)
    }


}