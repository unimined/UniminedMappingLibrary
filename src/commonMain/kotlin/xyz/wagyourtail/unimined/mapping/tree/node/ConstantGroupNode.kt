package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.TargetVisitor

class ConstantGroupNode(parent: MappingTree, val type: InlineType, val baseNs: Namespace) : BaseNode<ConstantGroupVisitor, MappingVisitor>(parent), ConstantGroupVisitor {
    private val _namespaces: MutableSet<Namespace> = mutableSetOf()

    val namespaces: Set<Namespace> get() = _namespaces
    val constants = mutableSetOf<ConstantNode>()
    val targets = mutableSetOf<TargetNode>()

    fun addNamespaces(namespace: Set<Namespace>) {
        root.mergeNs(namespace)
        _namespaces.addAll(namespace)
    }

    class ConstantNode(parent: ConstantGroupNode, val baseNs: Namespace, val constClass: InternalName, val constName: UnqualifiedName, val fieldDesc: FieldDescriptor?) : BaseNode<ConstantVisitor, ConstantGroupVisitor>(parent), ConstantVisitor {
        override fun acceptOuter(visitor: ConstantGroupVisitor, minimize: Boolean) = visitor.visitConstant(constClass, constName, fieldDesc)
    }

    class TargetNode(parent: ConstantGroupNode, val baseNs: Namespace, val target: FullyQualifiedName, val paramIdx: Int?) : BaseNode<TargetVisitor, ConstantGroupVisitor>(parent), TargetVisitor {
        override fun acceptOuter(visitor: ConstantGroupVisitor, minimize: Boolean) = visitor.visitTarget(target, paramIdx)
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
        constants.add(node)
        return node
    }

    override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        val node = TargetNode(this, baseNs, target, paramIdx)
        targets.add(node)
        return node
    }

    override fun acceptOuter(visitor: MappingVisitor, minimize: Boolean) = visitor.visitConstantGroup(type, baseNs, namespaces)

    override fun acceptInner(visitor: ConstantGroupVisitor, minimize: Boolean) {
        super.acceptInner(visitor, minimize)
        for (c in constants) {
            c.accept(visitor, minimize)
        }
        for (t in targets) {
            t.accept(visitor, minimize)
        }
    }
}