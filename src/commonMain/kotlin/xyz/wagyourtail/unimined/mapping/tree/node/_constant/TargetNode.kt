package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetVisitor

class TargetNode(parent: ConstantGroupNode, val baseNs: Namespace, val target: FullyQualifiedName, val paramIdx: Int?) : BaseNode<TargetVisitor, ConstantGroupVisitor>(parent),
    TargetVisitor {
    override fun acceptOuter(visitor: ConstantGroupVisitor, minimize: Boolean): TargetVisitor? {
        return visitor.visitTarget(target, paramIdx)
    }
}