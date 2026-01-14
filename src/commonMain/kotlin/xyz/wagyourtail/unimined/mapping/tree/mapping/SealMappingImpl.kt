package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptySealMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealMapping
import xyz.wagyourtail.unimined.mapping.visitor.SealMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealedType
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateSealMappingVisitor

class SealMappingImpl(
    parent: BaseMappingImpl<ClassMappingVisitor, *>?,
    override val type: SealedType,
    override val name: InternalName?,
    override val baseNs: Namespace
): BaseMappingImpl<SealMappingVisitor, ClassMappingVisitor>(parent), SealMapping, SealMappingVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): SealMappingVisitor? {
        return visitor.visitSeal(type, name, baseNs)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitSeal(EmptyClassMappingVisitor(), type, name, baseNs)
        if (inner) acceptInner(DelegateSealMappingVisitor(EmptySealMappingVisitor(), delegator), root.namespaces, true)
    }

}