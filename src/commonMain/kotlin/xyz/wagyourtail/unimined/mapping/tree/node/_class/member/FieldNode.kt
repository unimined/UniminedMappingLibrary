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

    override fun acceptOuter(visitor: ClassVisitor, minimize: Boolean): FieldVisitor? {
        val names = if (minimize) {
            val descNs = root.namespaces.firstOrNull { it in names }
            if (descNs != null) {
                names.mapValues { (ns, name) -> name to if (ns == descNs) getFieldDesc(ns) else null }
            } else {
                names.mapValues { (_, name) -> name to null }
            }
        } else {
            names.mapValues { (ns, name) -> name.let { name to descs[ns]?.getFieldDescriptor() } }
        }
        return visitor.visitField(names)
    }

}