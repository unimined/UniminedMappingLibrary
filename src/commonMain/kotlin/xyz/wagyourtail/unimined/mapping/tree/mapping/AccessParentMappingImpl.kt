package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentMapping
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.BaseMappingVisitor

abstract class AccessParentMappingImpl<T: AccessParentMappingVisitor, U: BaseMappingVisitor>(parent: BaseMappingImpl<U, *>?) : BaseMappingImpl<T, U>(parent), AccessParentMapping, AccessParentMappingVisitor {

    private val _access: MutableList<AccessMappingImpl<T>> = mutableListOf()
    override val access: List<AccessMappingImpl<T>> get() = _access

    override fun acceptInner(visitor: T, nsFilter: Collection<Namespace>, sort: Boolean) {
        for (access in if (sort) access.sortedBy { it.toString() } else access) {
            access.accept(visitor, nsFilter, sort)
        }
        super.acceptInner(visitor, nsFilter, sort)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions
    ): AccessMappingVisitor? {
        val node = AccessMappingImpl(this, type, value, condition)
        _access.add(node)
        return node
    }

}