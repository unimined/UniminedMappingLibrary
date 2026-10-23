package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface MethodMapping : InvokableMapping {

    val entries: Map<Namespace, MethodNameAndDescriptor>

    val names: Map<Namespace, UnqualifiedName>
        get() = entries.mapValues { it.value.name }

    val descs: Map<Namespace, MethodDescriptor>
        get() = entries.mapNotNullValues { it.value.descriptor }

    fun hasDescriptor() = entries.values.any { it.descriptor != null }

    companion object {
        operator fun invoke(
            entries: Map<Namespace, MethodNameAndDescriptor> = emptyMap(),
            parameters: Iterable<ParameterMapping> = emptyList(),
            localVariables: Iterable<LocalVariableMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MethodMapping = MutableMethodMapping(entries, parameters, localVariables, metadata)
    }

}
