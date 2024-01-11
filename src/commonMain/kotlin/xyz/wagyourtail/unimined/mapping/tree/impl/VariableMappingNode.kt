package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.VariableProperty
import xyz.wagyourtail.unimined.mapping.tree.VariablePropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedMemberVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class VariableMappingNode(parent: AbstractMappingNode<*>, override var lvOrdinal: Int, override var startOpIdx: Int?):
    AbstractNamespacedMappingNode<VariablePropertyView>(parent), VariableProperty {
    override val type = ElementType.VARIABLE

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitVariable(lvOrdinal, startOpIdx, namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedMemberVisitor(this) {}
    }
}