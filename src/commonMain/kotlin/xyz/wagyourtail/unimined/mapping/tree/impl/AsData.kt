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
import xyz.wagyourtail.unimined.mapping.toNsMap

internal fun mergeDescMismatch(hasDesc: MethodMappingData, noDesc: MethodMappingData) {
    for ((ns, e) in noDesc.entries) {
        if (ns !in hasDesc.entries) hasDesc.entries[ns] = e
    }
    for ((ns, e) in hasDesc.entries) {
        if (ns !in noDesc.entries) noDesc.entries[ns] = e.name.withMethodDesc(null)
    }
    hasDesc.mergeVersion++
    noDesc.mergeVersion++
    noDesc.mergeChildrenInto(hasDesc)
}

internal fun mergeDescMismatch(hasDesc: FieldMappingData, noDesc: FieldMappingData) {
    for ((ns, e) in noDesc.entries) {
        if (ns !in hasDesc.entries) hasDesc.entries[ns] = e
    }
    for ((ns, e) in hasDesc.entries) {
        if (ns !in noDesc.entries) noDesc.entries[ns] = e.name.withFieldDesc(null)
    }
    hasDesc.mergeVersion++
    noDesc.mergeVersion++
    noDesc.mergeChildrenInto(hasDesc)
}

// ---- foreign-impl -> default-impl conversions for factories/wrappers ----

internal fun PackageMapping.asData() = this as? PackageMappingData
    ?: PackageMappingData(names.toNsMap(), metadata.asMetadataDataList())

internal fun ClassMapping.asData() = this as? ClassMappingData
    ?: ClassMappingData(
        names.toNsMap(),
        methods.map { it.asData() }.toMutableList(),
        fields.map { it.asData() }.toMutableList(),
        wildcards.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun MethodMapping.asData() = this as? MethodMappingData
    ?: MethodMappingData(
        entries.toNsMap(),
        parameters.map { it.asData() }.toMutableList(),
        localVariables.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun FieldMapping.asData() = this as? FieldMappingData
    ?: FieldMappingData(entries.toNsMap(), metadata.asMetadataDataList())

internal fun WildcardMapping.asData() = this as? WildcardMappingData
    ?: WildcardMappingData(
        type,
        descs.toNsMap(),
        parameters.map { it.asData() }.toMutableList(),
        localVariables.map { it.asData() }.toMutableList(),
        metadata.asMetadataDataList()
    )

internal fun ParameterMapping.asData() = this as? ParameterMappingData
    ?: ParameterMappingData(index, lvOrd, names.toNsMap(), metadata.asMetadataDataList())

internal fun LocalVariableMapping.asData() = this as? LocalVariableMappingData
    ?: LocalVariableMappingData(lvOrd, startOp, names.toNsMap(), metadata.asMetadataDataList())

internal fun ConstantGroupMapping.asData() = this as? ConstantGroupMappingData
    ?: ConstantGroupMappingData(type, name, baseNs, metadata.asMetadataDataList())
