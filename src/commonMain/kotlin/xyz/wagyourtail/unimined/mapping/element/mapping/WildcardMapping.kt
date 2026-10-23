package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor

interface WildcardMapping : InvokableMapping {

    val type: WildcardType

    val descs: Map<Namespace, FieldOrMethodDescriptor>

    fun hasDescriptor() = descs.isNotEmpty()

    companion object {
        operator fun invoke(
            type: WildcardType,
            descs: Map<Namespace, FieldOrMethodDescriptor> = emptyMap(),
            parameters: Iterable<ParameterMapping> = emptyList(),
            localVariables: Iterable<LocalVariableMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): WildcardMapping = MutableWildcardMapping(type, descs, parameters, localVariables, metadata)
    }

}
