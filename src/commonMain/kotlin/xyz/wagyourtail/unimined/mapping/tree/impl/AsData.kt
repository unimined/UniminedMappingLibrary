@file:UseSerializers(
    NamespaceSerializer::class,
    InternalNameSerializer::class,
    UnqualifiedNameSerializer::class,
    FieldNameAndDescriptorSerializer::class,
    MethodNameAndDescriptorSerializer::class,
    FieldOrMethodDescriptorSerializer::class,
    FieldDescriptorSerializer::class,
    MethodDescriptorSerializer::class
)

package xyz.wagyourtail.unimined.mapping.tree.impl


import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.asMetadataDataList
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toSmallMap

internal fun mergeDescMismatch(hasDesc: MethodMappingData, noDesc: MethodMappingData) {
    hasDesc.setNames(noDesc.names.filter { it.key !in hasDesc.names })
    noDesc.setNames(hasDesc.names.filter { it.key !in noDesc.names })
    noDesc.mergeChildrenInto(hasDesc)
}

internal fun mergeDescMismatch(hasDesc: FieldMappingData, noDesc: FieldMappingData) {
    hasDesc.setNames(noDesc.names.filter { it.key !in hasDesc.names })
    noDesc.setNames(hasDesc.names.filter { it.key !in noDesc.names })
    noDesc.mergeChildrenInto(hasDesc)
}

// ---- foreign-impl -> default-impl conversions for factories/wrappers ----

internal fun PackageMapping.asData() = this as? PackageMappingData
    ?: PackageMappingData(names.toSmallMap(), metadata.asMetadataDataList())

internal fun ClassMapping.asData() = this as? ClassMappingData
    ?: ClassMappingData(
        names.toSmallMap(),
        methods.map { it.asData() }.toMutableList(),
        fields.map { it.asData() }.toMutableList(),
        wildcards.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun MethodMapping.asData() = this as? MethodMappingData
    ?: MethodMappingData(
        entries.toSmallMap(),
        parameters.map { it.asData() }.toMutableList(),
        localVariables.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun FieldMapping.asData() = this as? FieldMappingData
    ?: FieldMappingData(entries.toSmallMap(), metadata.asMetadataDataList())

internal fun WildcardMapping.asData() = this as? WildcardMappingData
    ?: WildcardMappingData(
        type,
        descs.toSmallMap(),
        parameters.map { it.asData() }.toMutableList(),
        localVariables.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun ParameterMapping.asData() = this as? ParameterMappingData
    ?: ParameterMappingData(index, lvOrd, names.toSmallMap(), metadata.asMetadataDataList())

internal fun LocalVariableMapping.asData() = this as? LocalVariableMappingData
    ?: LocalVariableMappingData(lvOrd, startOp, names.toSmallMap(), metadata.asMetadataDataList())

internal fun ConstantGroupMapping.asData() = this as? ConstantGroupMappingData
    ?: ConstantGroupMappingData(type, name, baseNs, metadata.asMetadataDataList())
