package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SealedType

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

}