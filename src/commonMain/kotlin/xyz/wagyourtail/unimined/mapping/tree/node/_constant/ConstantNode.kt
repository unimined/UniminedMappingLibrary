package xyz.wagyourtail.unimined.mapping.tree.node._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
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
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateConstantVisitor

class ConstantNode(parent: ConstantGroupNode, val baseNs: Namespace, val constClass: InternalName, val constName: UnqualifiedName, val fieldDesc: FieldDescriptor?) : BaseNode<ConstantVisitor, ConstantGroupVisitor>(parent),
    ConstantVisitor {

    fun asFullyQualifiedName() = FullyQualifiedName(ObjectType(constClass), NameAndDescriptor(constName, fieldDesc?.let { FieldOrMethodDescriptor(it) }))

    override fun acceptOuter(visitor: ConstantGroupVisitor, nsFilter: Collection<Namespace>): ConstantVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in (parent as ConstantGroupNode).namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val mapped = root.map(baseNs, first,
                FullyQualifiedName(
                    ObjectType(constClass),
                    NameAndDescriptor(constName, fieldDesc?.let { FieldOrMethodDescriptor(it) })
                )
            )
            val (constClass, constName) = mapped.getParts()
            val (fieldName, fieldDesc) = constName!!.getParts()
            return visitor.visitConstant(constClass.getInternalName(), fieldName, fieldDesc?.getFieldDescriptor())
        }
        return visitor.visitConstant(constClass, constName, fieldDesc)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitConstant(EmptyConstantGroupVisitor(), constClass, constName, fieldDesc)
        if (inner) acceptInner(DelegateConstantVisitor(EmptyConstantVisitor(), delegator), root.namespaces, true)
    }

}