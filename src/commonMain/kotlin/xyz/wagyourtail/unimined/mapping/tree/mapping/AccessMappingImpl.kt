package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.visitor.AddRemove
import xyz.wagyourtail.unimined.mapping.visitor.AccessMapping
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAccessParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAccessMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateAccessMappingVisitor

class AccessMappingImpl<U: AccessParentMappingVisitor>(
    parent: BaseMappingImpl<U, *>,
    override val type: AddRemove,
    override val value: AccessFlag,
    override val condition: AccessConditions
) : BaseMappingImpl<AccessMappingVisitor, U>(parent), AccessMapping, AccessMappingVisitor {

    override fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): AccessMappingVisitor? {
        return visitor.visitAccess(type, value, condition)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitAccess(EmptyAccessParentMappingVisitor(), type, value, condition)
        if (inner) acceptInner(DelegateAccessMappingVisitor(EmptyAccessMappingVisitor(), delegator), root.namespaces, true)
    }

}
