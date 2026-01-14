package xyz.wagyourtail.unimined.mapping.tree.mapping._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyTargetMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetMapping
import xyz.wagyourtail.unimined.mapping.visitor.TargetMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateTargetMappingVisitor

class TargetMappingImpl(
    parent: ConstantGroupMappingImpl,
    val baseNs: Namespace,
    override val target: FullyQualifiedName?,
    override val paramIdx: Int?
) : BaseMappingImpl<TargetMappingVisitor, ConstantGroupMappingVisitor>(parent),
    TargetMapping, TargetMappingVisitor {
    override fun acceptOuter(visitor: ConstantGroupMappingVisitor, nsFilter: Collection<Namespace>): TargetMappingVisitor? {
        return if (baseNs !in nsFilter && target != null) {
    //            val ns = nsFilter.filter { it in (parent as ConstantGroupMappingImpl).namespaces }.toSet()
    //            if (ns.isEmpty()) return null
    //            val first = ns.first()
    //            val mapped = root.map(baseNs, first, target)
    //            return visitor.visitTarget(mapped, paramIdx)
            null
        } else {
            visitor.visitTarget(target, paramIdx)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitTarget(EmptyConstantGroupMappingVisitor(), target, paramIdx)
        if (inner) acceptInner(DelegateTargetMappingVisitor(EmptyTargetMappingVisitor(), delegator), root.namespaces, true)
    }

}