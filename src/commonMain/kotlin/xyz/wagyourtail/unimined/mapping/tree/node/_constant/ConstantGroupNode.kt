package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetVisitor

class ConstantGroupNode(parent: AbstractMappingTree, val type: InlineType, val name: String?, val baseNs: Namespace) : BaseNode<ConstantGroupVisitor, MappingVisitor>(parent), ConstantGroupVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()

    private val _constants = mutableSetOf<ConstantNode>()
    private val _targets = mutableSetOf<TargetNode>()

    val namespaces: Set<Namespace> get() = _namespaces

    val constants: Set<ConstantNode> get() = _constants
    val targets: Set<TargetNode> get() = _targets

    fun addNamespaces(namespace: Set<Namespace>) {
        root.mergeNs(namespace)
        _namespaces.addAll(namespace)
    }

    enum class InlineType {
        PLAIN,
        BITFIELD
    }

    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        val node = ConstantNode(this, baseNs, fieldClass, fieldName, fieldDesc)
        _constants.add(node)
        return node
    }

    override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        val node = TargetNode(this, baseNs, target, paramIdx)
        _targets.add(node)
        return node
    }

    override fun acceptOuter(visitor: MappingVisitor, nsFilter: Collection<Namespace>): ConstantGroupVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            return visitor.visitConstantGroup(type, name, first, ns - first)
        } else {
            return visitor.visitConstantGroup(type, name, baseNs, nsFilter.filter { it in namespaces }.toSet() - baseNs)
        }
    }

    override fun acceptInner(visitor: ConstantGroupVisitor, nsFilter: Collection<Namespace>) {
        super.acceptInner(visitor, nsFilter)
        for (c in _constants) {
            c.accept(visitor, nsFilter)
        }
        for (t in _targets) {
            t.accept(visitor, nsFilter)
        }
    }
}