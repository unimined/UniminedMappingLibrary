package xyz.wagyourtail.unimined.mapping.tree.mapping._class

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.AccessParentMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateInnerClassMappingVisitor

class InnerClassMappingImpl(
    parent: ClassMappingImpl,
    override val type: InnerType
) : AccessParentMappingImpl<InnerClassMappingVisitor, ClassMappingVisitor>(parent), InnerClassMapping, InnerClassMappingVisitor {
    private val _names: MutableMap<Namespace, Pair<String, FullyQualifiedName?>> = mutableMapOf()

    override val names: Map<Namespace, Pair<String, FullyQualifiedName?>> get() = _names

    fun setNames(names: Map<Namespace, Pair<String, FullyQualifiedName?>>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): InnerClassMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitInnerClass(type, names)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitInnerClass(EmptyClassMappingVisitor(), type, names)
        if (inner) acceptInner(DelegateInnerClassMappingVisitor(EmptyInnerClassMappingVisitor(), delegator), root.namespaces, true)
    }

}