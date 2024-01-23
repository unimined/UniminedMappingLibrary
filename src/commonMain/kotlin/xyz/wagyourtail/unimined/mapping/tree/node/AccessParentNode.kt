package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessVisitor
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

abstract class AccessParentNode<T: AccessParentVisitor<T>, U: BaseVisitor<U>>(parent: BaseNode<U, *>?) : BaseNode<T, U>(parent), AccessParentVisitor<T> {

    private val _access: MutableMap<AccessFlag, AccessNode<T>> = mutableMapOf()
    val access: Map<AccessFlag, AccessNode<T>> get() = _access

    override fun acceptInner(visitor: T, minimize: Boolean) {
        for (access in access.values) {
            access.accept(visitor, minimize)
        }
        super.acceptInner(visitor, minimize)
    }

    override fun visitAccess(type: AccessType, value: AccessFlag, namespaces: Set<Namespace>): AccessVisitor? {
        val node = AccessNode(this, type, value)
        node.addNamespaces(namespaces)
        _access[value] = node
        return node
    }

}