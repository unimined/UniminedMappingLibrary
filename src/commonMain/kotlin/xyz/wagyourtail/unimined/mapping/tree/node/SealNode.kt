package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptySealVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealedType
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateSealVisitor

class SealNode(parent: BaseNode<ClassVisitor, *>?, val type: SealedType, val name: InternalName?, val baseNs: Namespace): BaseNode<SealVisitor, ClassVisitor>(parent), SealVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): SealVisitor? {
        return visitor.visitSeal(type, name, baseNs, namespaces)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitSeal(EmptyClassVisitor(), type, name, baseNs, namespaces)
        if (inner) acceptInner(DelegateSealVisitor(EmptySealVisitor(), delegator), root.namespaces, true)
    }

}