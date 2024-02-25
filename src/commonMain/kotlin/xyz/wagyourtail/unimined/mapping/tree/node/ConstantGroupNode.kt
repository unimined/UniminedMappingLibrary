package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
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
        override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: List<Namespace>, minimize: Boolean): ConstantVisitor? {
            if (baseNs !in nsFilter) {
                val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
                if (ns.isEmpty()) return null
                val first = ns.first()
                val mapped = root.map(baseNs, first, FullyQualifiedName(ObjectType(constClass), NameAndDescriptor(constName, fieldDesc?.let { FieldOrMethodDescriptor(it) })))
                val (constClass, constName) = mapped.getParts()
                val (fieldName, fieldDesc) = constName!!.getParts()
                return visitor.visitConstant(constClass.getInternalName(), fieldName, fieldDesc?.getFieldDescriptor())
            }
            return visitor.visitConstant(constClass, constName, fieldDesc)
        }
    }

    class TargetNode(parent: ConstantGroupNode, val baseNs: Namespace, val target: FullyQualifiedName, val paramIdx: Int?) : BaseNode<TargetVisitor, ConstantGroupVisitor>(parent), TargetVisitor {
        override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: List<Namespace>, minimize: Boolean): TargetVisitor? {
            if (baseNs !in nsFilter) {
                val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
                if (ns.isEmpty()) return null
                val first = ns.first()
                val mapped = root.map(baseNs, first, target)
                return visitor.visitTarget(mapped, paramIdx)
            }
            return visitor.visitTarget(target, paramIdx)
        }
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

    override fun acceptOuter(visitor: MappingVisitor, nsFilter: List<Namespace>, minimize: Boolean): ConstantGroupVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            return visitor.visitConstantGroup(type, first, ns - first)
        } else {
            return visitor.visitConstantGroup(type, baseNs, nsFilter.filter { it in namespaces }.toSet() - baseNs)
        }
    }

    override fun acceptInner(visitor: ConstantGroupVisitor, nsFilter: List<Namespace>, minimize: Boolean) {
        super.acceptInner(visitor, nsFilter, minimize)
        for (c in constants) {
            c.accept(visitor, nsFilter, minimize)
        }
        for (t in targets) {
            t.accept(visitor, nsFilter, minimize)
        }
    }
}