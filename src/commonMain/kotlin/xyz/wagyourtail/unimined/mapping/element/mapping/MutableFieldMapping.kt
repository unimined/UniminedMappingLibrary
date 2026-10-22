package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.FieldMappingData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface MutableFieldMapping : FieldMapping, MutableMetadataParent {

    fun setNames(names: Map<Namespace, UnqualifiedName>)

    fun setDescriptors(descs: Map<Namespace, FieldDescriptor>)

    fun setEntries(entries: Map<Namespace, FieldNameAndDescriptor>)

    companion object {
        operator fun invoke(
            entries: Map<Namespace, FieldNameAndDescriptor> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableFieldMapping = FieldMappingData(entries.toMutableMap(), metadata.asMetadataDataList())
    }

}
