package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.FieldExpression
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyExpressionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExpressionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateExpressionVisitor

class ExpressionNode(parent: ConstantGroupNode, val baseNs: Namespace, val value: Constant, val expression: Expression): BaseNode<ExpressionVisitor, ConstantGroupVisitor>(parent), ExpressionVisitor {

    override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: Collection<Namespace>): ExpressionVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val mapped = root.map(baseNs, first, expression)
            return visitor.visitExpression(value, mapped)
        }
        return visitor.visitExpression(value, expression)
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
                        val fd = cls?.getFields(fromNs, name.value, desc)?.map { it.getName(toNs) }
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
        delegator.namespaces = root.namespaces
        delegator.visitExpression(EmptyConstantGroupVisitor(), value, expression)
        if (inner) acceptInner(DelegateExpressionVisitor(EmptyExpressionVisitor(), delegator), root.namespaces, true)
    }

}