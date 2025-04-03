package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

abstract class AccessParentNode<T: AccessParentVisitor<T>, U: BaseVisitor<U>>(parent: BaseNode<U, *>?) : BaseNode<T, U>(parent), AccessParentVisitor<T> {

    private val _access: MutableList<AccessNode<T>> = mutableListOf()
    val access: List<AccessNode<T>> get() = _access

    override fun acceptInner(visitor: T, nsFilter: Collection<Namespace>) {
        for (access in access.sortedBy { it.toString() }) {
            access.accept(visitor, nsFilter)
        }
        super.acceptInner(visitor, nsFilter)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        val node = AccessNode(this, type, value, condition)
        node.addNamespaces(namespaces)
        _access.add(node)
        return node
    }

}