package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class CommentMappingNode(parent: AbstractMappingNode<*>): AbstractNamespacedMappingNode<CommentMappingNode>(parent) {
    override val type = ElementType.COMMENT

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitComment(namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedVisitor(this) {}
    }
}