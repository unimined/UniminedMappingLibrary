package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.MethodMappingData
import xyz.wagyourtail.unimined.mapping.tree.impl.asData
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface MutableMethodMapping : MethodMapping, MutableInvokableMapping {

    fun setNames(names: Map<Namespace, UnqualifiedName>)

    fun setDescriptors(descs: Map<Namespace, MethodDescriptor>)

    fun setEntries(entries: Map<Namespace, MethodNameAndDescriptor>)

    companion object {
        operator fun invoke(
            entries: Map<Namespace, MethodNameAndDescriptor> = emptyMap(),
            parameters: Iterable<ParameterMapping> = emptyList(),
            localVariables: Iterable<LocalVariableMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableMethodMapping = MethodMappingData(
            entries.toMutableMap(),
            parameters.map { it.asData() }.toMutableList(),
            localVariables.map { it.asData() }.toMutableList(),
            metadata.asMetadataDataList()
        )
    }

}
