package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyTargetVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateTargetVisitor

class TargetNode(parent: ConstantGroupNode, val baseNs: Namespace, val target: FullyQualifiedName?, val paramIdx: Int?) : BaseNode<TargetVisitor, ConstantGroupVisitor>(parent),
    TargetVisitor {
    override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: Collection<Namespace>): TargetVisitor? {
        if (baseNs !in nsFilter && target != null) {
            val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val mapped = root.map(baseNs, first, target)
            return visitor.visitTarget(mapped, paramIdx)
        }
        return visitor.visitTarget(target, paramIdx)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitTarget(EmptyConstantGroupVisitor(), target, paramIdx)
        if (inner) acceptInner(DelegateTargetVisitor(EmptyTargetVisitor(), delegator), root.namespaces)
    }

}