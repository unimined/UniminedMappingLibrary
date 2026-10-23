package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.asMetadataDataList
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.toSmallMap
import xyz.wagyourtail.unimined.mapping.tree.impl.WildcardMappingData
import xyz.wagyourtail.unimined.mapping.tree.impl.asData

interface MutableWildcardMapping : WildcardMapping, MutableInvokableMapping {

    fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>)

    companion object {
        operator fun invoke(
            type: WildcardType,
            descs: Map<Namespace, FieldOrMethodDescriptor> = emptyMap(),
            parameters: Iterable<ParameterMapping> = emptyList(),
            localVariables: Iterable<LocalVariableMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableWildcardMapping = WildcardMappingData(
            type,
            descs.toSmallMap(),
            parameters.map { it.asData() }.toMutableList(),
            localVariables.map { it.asData() }.toMutableList(),
            metadata.asMetadataDataList()
        )
    }

}
