package xyz.wagyourtail.unimined.mapping.tree.mapping._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyExpressionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExpressionMapping
import xyz.wagyourtail.unimined.mapping.visitor.ExpressionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateExpressionMappingVisitor

class ExpressionMappingImpl(
    parent: ConstantGroupMappingImpl,
    val baseNs: Namespace,
    override val value: Constant,
    override val expression: Expression
): BaseMappingImpl<ExpressionMappingVisitor, ConstantGroupMappingVisitor>(parent), ExpressionMapping, ExpressionMappingVisitor {

    override fun acceptOuter(visitor: ConstantGroupMappingVisitor, nsFilter: Collection<Namespace>): ExpressionMappingVisitor? {
        return if (baseNs !in nsFilter) {
            null
    //            val ns = nsFilter.filter { it in (parent as ConstantGroupMappingImpl).namespaces }.toSet()
    //            if (ns.isEmpty()) return null
    //            val first = ns.first()
    //            val mapped = root.map(baseNs, first, expression)
    //            return visitor.visitExpression(value, mapped)
        } else {
            visitor.visitExpression(value, expression)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitExpression(EmptyConstantGroupMappingVisitor(), value, expression)
        if (inner) acceptInner(DelegateExpressionMappingVisitor(EmptyExpressionMappingVisitor(), delegator), root.namespaces, true)
    }

}