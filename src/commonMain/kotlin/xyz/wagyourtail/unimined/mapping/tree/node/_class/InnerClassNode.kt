package xyz.wagyourtail.unimined.mapping.tree.node._class

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.AccessParentNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InnerClassVisitor

class InnerClassNode(parent: ClassNode, val innerType: InnerType) : AccessParentNode<InnerClassVisitor, ClassVisitor>(parent), InnerClassVisitor {
    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    private val _targets: MutableMap<Namespace, FullyQualifiedName> = mutableMapOf()

    val names: Map<Namespace, String> get() = _names
    val targets: Map<Namespace, FullyQualifiedName> get() = _targets

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    fun setTargets(targets: Map<Namespace, FullyQualifiedName>) {
        root.mergeNs(targets.keys)
        this._targets.putAll(targets)
    }

    enum class InnerType {
        INNER,
        LOCAL,
        ANONYMOUS,
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>, minimize: Boolean) = visitor.visitInnerClass(innerType, names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name to targets[ns] })

}