package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.minus
import xyz.wagyourtail.unimined.mapping.jvms.four.plus
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateAccessVisitor

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

    fun apply(set: MutableSet<AccessFlag>) {
        if (conditions.check(set)) {
            if (accessType == AccessType.ADD) {
                set.add(accessFlag)
            } else if (accessType == AccessType.REMOVE) {
                set.remove(accessFlag)
            }
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitAccess(EmptyAccessParentVisitor(), accessType, accessFlag, conditions, namespaces)
        if (inner) acceptInner(DelegateAccessVisitor(EmptyAccessVisitor(), delegator), root.namespaces)
    }

}