package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InterfaceVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InterfacesType

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

}