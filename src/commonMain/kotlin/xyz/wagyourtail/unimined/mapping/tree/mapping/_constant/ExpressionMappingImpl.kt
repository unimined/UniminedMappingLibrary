package xyz.wagyourtail.unimined.mapping.tree.mapping._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.FieldExpression
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
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

    fun AbstractMappingTree.map(fromNs: Namespace, toNs: Namespace, expression: Expression): Expression {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return expression
        return Expression.unchecked(buildString {
            expression.accept(expressionRemapAcceptor(this@map, fromNs, toNs))
        })
    }

    private fun StringBuilder.expressionRemapAcceptor(tree: AbstractMappingTree, fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is FieldExpression -> {
                    val (owner, instance, nameAndDesc) = obj.getParts()
                    val cls = if (owner != null) tree.getClass(fromNs, owner.getInternalName()) else null
                    if (owner != null) {
                        val mappedOwner = cls?.getName(toNs)
                        if (mappedOwner != null) {
                            append(mappedOwner)
                        } else {
                            append(owner)
                        }
                    }
                    if (instance) {
                        append("this.")
                    }
                    val (name, desc) = nameAndDesc
                    if (owner != null) {
                        val fd = cls?.getFields(fromNs, name, desc)?.map { it.getName(toNs) }
                        if (!fd.isNullOrEmpty()) {
                            val mappedName = fd.first()!!
                            append(mappedName)
                        } else {
                            append(name)
                        }
                    } else {
                        append(name)
                    }
                    append(";")
                    if (desc != null) {
                        append(tree.map(fromNs, toNs, desc))
                    }
                    false
                }
                else -> {
                    if (leaf) {
                        append(obj.toString())
                    }
                    true
                }
            }
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitExpression(EmptyConstantGroupMappingVisitor(), value, expression)
        if (inner) acceptInner(DelegateExpressionMappingVisitor(EmptyExpressionMappingVisitor(), delegator), root.namespaces, true)
    }

}