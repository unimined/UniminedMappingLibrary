package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.util.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InnerClassVisitor

class InnerClassNode(parent: ClassNode, val innerType: InnerType) : BaseNode<InnerClassVisitor, ClassVisitor>(parent), InnerClassVisitor {
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

    override fun acceptOuter(visitor: ClassVisitor, minimize: Boolean) = visitor.visitInnerClass(innerType, names.mapValues { (ns, name) -> name to targets[ns] })

}