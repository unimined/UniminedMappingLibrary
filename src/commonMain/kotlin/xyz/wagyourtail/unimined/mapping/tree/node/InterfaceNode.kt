package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyInterfaceVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InterfaceVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InterfacesType
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateInterfaceVisitor

class InterfaceNode(parent: BaseNode<ClassVisitor, *>?, val type: InterfacesType, val name: InternalName, val baseNs: Namespace): BaseNode<InterfaceVisitor, ClassVisitor>(parent), InterfaceVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): InterfaceVisitor? {
        return visitor.visitInterface(type, name, baseNs, namespaces)
    }

    override fun toUMF(inner: Boolean) = buildString{
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitInterface(EmptyClassVisitor(), type, name, baseNs, namespaces)
        if (inner) acceptInner(DelegateInterfaceVisitor(EmptyInterfaceVisitor(), delegator), root.namespaces, true)
    }

}