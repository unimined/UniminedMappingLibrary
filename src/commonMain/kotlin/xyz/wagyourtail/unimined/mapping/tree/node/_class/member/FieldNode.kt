package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor

class FieldNode(parent: ClassNode): FieldMethodResolvable<FieldNode, FieldVisitor, FieldVisitor>(parent, ::FieldNode), FieldVisitor {

    fun getFieldDesc(namespace: Namespace) = getDescriptor(namespace)?.getFieldDescriptor()

    fun setFieldDescs(descs: Map<Namespace, FieldDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldOrMethodDescriptor.unchecked(it.value.toString()) })
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): FieldVisitor? {
        val names = names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let { name to getFieldDesc(ns) } }
        if (names.isEmpty()) return null
        return visitor.visitField(names)
    }

}