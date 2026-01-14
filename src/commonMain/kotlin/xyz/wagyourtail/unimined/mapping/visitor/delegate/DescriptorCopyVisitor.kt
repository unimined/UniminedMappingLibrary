package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*

class DescriptorCopyDelegator(val context: AbstractMappingTree) : Delegator() {

    override fun visitMethod(
        delegate: ClassMappingVisitor,
        names: Map<Namespace, MethodNameAndDescriptor>
    ): MethodMappingVisitor? {
        val descriptor = names.entries.firstOrNull { it.value.descriptor != null }
        return if (descriptor == null) {
            super.visitMethod(delegate, names)
        } else {
            val newNames = mutableMapOf<Namespace, MethodNameAndDescriptor>()
            val fromNs = descriptor.key
            val fromDesc = descriptor.value.descriptor!!

            for ((ns, value) in names) {
                if (value.descriptor == null) {
                    val mapped = context.map(fromNs, ns, fromDesc)
                    newNames[ns] = value.name.withMethodDesc(mapped)
                } else {
                    newNames[ns] = value
                }
            }

            super.visitMethod(delegate, newNames)
        }

    }

    override fun visitField(
        delegate: ClassMappingVisitor,
        names: Map<Namespace, FieldNameAndDescriptor>
    ): FieldMappingVisitor? {
        val descriptor = names.entries.firstOrNull { it.value.descriptor != null }
        return if (descriptor == null) {
            super.visitField(delegate, names)
        } else {
            val newNames = mutableMapOf<Namespace, FieldNameAndDescriptor>()
            val fromNs = descriptor.key
            val fromDesc = descriptor.value.descriptor!!

            for ((ns, value) in names) {
                if (value.descriptor == null) {
                    val mapped = context.map(fromNs, ns, fromDesc)
                    newNames[ns] = value.name.withFieldDesc(mapped)
                } else {
                    newNames[ns] = value
                }
            }

            super.visitField(delegate, newNames)
        }
    }

    override fun visitWildcard(
        delegate: ClassMappingVisitor,
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
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

fun RootMappingVisitor.copyDescriptors(context: AbstractMappingTree): RootMappingVisitor {
    return delegator(DescriptorCopyDelegator(context))
}