package xyz.wagyourtail.unimined.mapping.tree.mapping._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateConstantGroupMappingVisitor

class ConstantGroupMappingImpl(
    parent: AbstractMappingTree,
    override val type: InlineType,
    override val name: String?,
    override val baseNs: Namespace
) : BaseMappingImpl<ConstantGroupMappingVisitor, RootMappingVisitor>(parent), ConstantGroupMapping, ConstantGroupMappingVisitor {

    private val _constants = mutableListOf<ConstantMappingImpl>()
    private val _targets = mutableListOf<TargetMappingImpl>()
    private val _expressions = mutableListOf<ExpressionMappingImpl>()

    override val constants: List<ConstantMappingImpl> get() = _constants
    override val targets: List<TargetMappingImpl> get() = _targets
    override val expressions: List<ExpressionMappingImpl> get() = _expressions

    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantMappingVisitor {
        val node = ConstantMappingImpl(this, baseNs, fieldClass, FieldNameAndDescriptor(fieldName, fieldDesc))
        _constants.add(node)
        return node
    }

    override fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetMappingVisitor {
        val node = TargetMappingImpl(this, baseNs, target, paramIdx)
        _targets.add(node)
        return node
    }

    override fun visitExpression(value: Constant, expression: Expression): ExpressionMappingVisitor? {
        val node = ExpressionMappingImpl(this, baseNs, value, expression)
        _expressions.add(node)
        return node
    }

    override fun acceptOuter(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>): ConstantGroupMappingVisitor? {
        return if (baseNs !in nsFilter) {
            null
        } else {
            visitor.visitConstantGroup(type, name, baseNs)
        }
    }

    override fun acceptInner(visitor: ConstantGroupMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (c in if (sort) constants.sortedBy { it.toString() } else constants) {
            c.accept(visitor, nsFilter, sort)
        }
        for (t in if (sort) targets.sortedBy { it.toString() } else targets) {
            t.accept(visitor, nsFilter, sort)
        }
        for (e in if (sort) expressions.sortedBy { it.toString() } else expressions) {
            e.accept(visitor, nsFilter, sort)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitConstantGroup(EmptyRootMappingVisitor(), type, name, baseNs)
        if (inner) acceptInner(DelegateConstantGroupMappingVisitor(EmptyConstantGroupMappingVisitor(), delegator), root.namespaces, true)
    }

}