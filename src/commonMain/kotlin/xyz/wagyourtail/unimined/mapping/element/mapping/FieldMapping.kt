package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface FieldMapping : MappingElement, MetadataParent {

    val entries: Map<Namespace, FieldNameAndDescriptor>

    val names: Map<Namespace, UnqualifiedName>
        get() = entries.mapValues { it.value.name }

    val descs: Map<Namespace, FieldDescriptor>
        get() = entries.mapNotNullValues { it.value.descriptor }

    fun hasDescriptor() = entries.values.any { it.descriptor != null }

    companion object {
        operator fun invoke(
            entries: Map<Namespace, FieldNameAndDescriptor> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): FieldMapping = MutableFieldMapping(entries, metadata)
    }

}
