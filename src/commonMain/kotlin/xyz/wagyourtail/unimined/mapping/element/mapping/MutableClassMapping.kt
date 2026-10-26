package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.asMetadataDataList
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.toNsMap
import xyz.wagyourtail.unimined.mapping.tree.impl.ClassMappingData
import xyz.wagyourtail.unimined.mapping.tree.impl.asData

interface MutableClassMapping : ClassMapping, MutableMetadataParent {

    fun setNames(names: Map<Namespace, InternalName>)

    /**
     * find or create a method node matching the given per-ns entries (merge-by-identity)
     */
    fun method(entries: Map<Namespace, MethodNameAndDescriptor>): MutableMethodMapping

    /**
     * find or create a field node matching the given per-ns entries (merge-by-identity)
     */
    fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping

    /**
     * find or create a wildcard node matching the given type/descs (merge-by-identity)
     */
    fun wildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): MutableWildcardMapping

    companion object {
        operator fun invoke(
            names: Map<Namespace, InternalName> = emptyMap(),
            methods: Iterable<MethodMapping> = emptyList(),
            fields: Iterable<FieldMapping> = emptyList(),
            wildcards: Iterable<WildcardMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableClassMapping = ClassMappingData(
            names.toNsMap(),
            methods.map { it.asData() }.toMutableList(),
            fields.map { it.asData() }.toMutableList(),
            wildcards.map { it.asData() }.toMutableList(),
            metadata.asMetadataDataList()
        )
    }

}
