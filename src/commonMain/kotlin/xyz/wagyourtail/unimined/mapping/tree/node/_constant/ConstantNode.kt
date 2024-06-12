package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantVisitor

class ConstantNode(parent: ConstantGroupNode, val baseNs: Namespace, val constClass: InternalName, val constName: UnqualifiedName, val fieldDesc: FieldDescriptor?) : BaseNode<ConstantVisitor, ConstantGroupVisitor>(parent),
    ConstantVisitor {

    fun asFullyQualifiedName() = FullyQualifiedName(ObjectType(constClass), NameAndDescriptor(constName, fieldDesc?.let { FieldOrMethodDescriptor(it) }))

    override fun acceptOuter(visitor: ConstantGroupVisitor, minimize: Boolean): ConstantVisitor? {
        return visitor.visitConstant(constClass, constName, fieldDesc)
    }
}