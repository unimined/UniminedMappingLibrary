package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.ParameterProperty
import xyz.wagyourtail.unimined.mapping.tree.ParameterPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedMemberVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class ParameterMappingNode internal constructor(
    parent: AbstractMappingNode<*>,
    override var index: Int? = null,
    override var lvOrdinal: Int? = null
): AbstractNamespacedMappingNode<ParameterPropertyView>(parent), ParameterProperty {
    override val type = ElementType.PARAMETER

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitParameter(index, lvOrdinal, namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedMemberVisitor(this) {}
    }
}