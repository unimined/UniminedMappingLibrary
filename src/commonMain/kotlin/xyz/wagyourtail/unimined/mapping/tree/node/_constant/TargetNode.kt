package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetVisitor

class TargetNode(parent: ConstantGroupNode, val baseNs: Namespace, val target: FullyQualifiedName, val paramIdx: Int?) : BaseNode<TargetVisitor, ConstantGroupVisitor>(parent),
    TargetVisitor {
    override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: Collection<Namespace>, minimize: Boolean): TargetVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val mapped = root.map(baseNs, first, target)
            return visitor.visitTarget(mapped, paramIdx)
        }
        return visitor.visitTarget(target, paramIdx)
    }
}