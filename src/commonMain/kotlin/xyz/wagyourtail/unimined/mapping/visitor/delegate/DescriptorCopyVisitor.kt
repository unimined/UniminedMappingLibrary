package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.WildcardVisitor

class DescriptorCopyDelegator(val context: MemoryMappingTree) : Delegator() {

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val descriptor = names.entries.firstOrNull { it.value.second != null }
        return if (descriptor == null) {
            super.visitMethod(delegate, names)
        } else {
            val newNames = mutableMapOf<Namespace, Pair<String, MethodDescriptor>>()
            val fromNs = descriptor.key
            val fromDesc = descriptor.value.second!!

            for ((ns, value) in names) {
                if (value.second == null) {
                    val mapped = context.map(fromNs, ns, fromDesc)
                    newNames[ns] = value.first to mapped
                } else {
                    newNames[ns] = value.first to value.second!!
                }
            }

            super.visitMethod(delegate, newNames)
        }

    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val descriptor = names.entries.firstOrNull { it.value.second != null }
        return if (descriptor == null) {
            super.visitField(delegate, names)
        } else {
            val newNames = mutableMapOf<Namespace, Pair<String, FieldDescriptor>>()
            val fromNs = descriptor.key
            val fromDesc = descriptor.value.second!!

            for ((ns, value) in names) {
                if (value.second == null) {
                    val mapped = context.map(fromNs, ns, fromDesc)
                    newNames[ns] = value.first to mapped
                } else {
                    newNames[ns] = value.first to value.second!!
                }
            }

            super.visitField(delegate, newNames)
        }
    }

    override fun visitWildcard(
        delegate: ClassVisitor,
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        if (descs.isEmpty()) return null
        val (fromNs, fromDesc) = descs.entries.first()
        val newDescs = mutableMapOf<Namespace, FieldOrMethodDescriptor>()
        for (namespace in context.namespaces) {
            if (namespace in descs) {
                newDescs[namespace] = descs[namespace]!!
            } else {
                val mapped = context.map(fromNs, namespace, fromDesc)
                newDescs[namespace] = mapped
            }
        }
        return super.visitWildcard(delegate, type, newDescs)
    }

}

fun MappingVisitor.copyDescriptors(context: MemoryMappingTree): MappingVisitor {
    return delegator(DescriptorCopyDelegator(context))
}