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


import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toSmallMap

@Serializable
data class ClassMappingData(
    override val names: MutableMap<Namespace, InternalName> = mutableMapOf(),
    override val methods: MutableList<MethodMappingData> = mutableListOf(),
    override val fields: MutableList<FieldMappingData> = mutableListOf(),
    override val wildcards: MutableList<WildcardMappingData> = mutableListOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableClassMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null
        set(value) {
            field = value
            methods.forEach { it.descRemapper = value }
            fields.forEach { it.descRemapper = value }
            wildcards.forEach { it.descRemapper = value }
        }

    override fun setNames(names: Map<Namespace, InternalName>) {
        this.names.putAll(names)
    }

    override fun method(entries: Map<Namespace, MethodNameAndDescriptor>): MutableMethodMapping {
        val incoming = MethodMappingData(entries.toSmallMap())
        incoming.descRemapper = descRemapper
        for (existing in methods) {
            if (incoming.mergeInto(existing)) return existing
        }
        methods.add(incoming)
        return incoming
    }

    override fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping {
        val incoming = FieldMappingData(entries.toSmallMap())
        incoming.descRemapper = descRemapper
        for (existing in fields) {
            if (incoming.mergeInto(existing)) return existing
        }
        fields.add(incoming)
        return incoming
    }

    override fun wildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): MutableWildcardMapping {
        val incoming = WildcardMappingData(type, descs.toSmallMap())
        incoming.descRemapper = descRemapper
        for (existing in wildcards) {
            if (incoming.mergeInto(existing)) return existing
        }
        wildcards.add(incoming)
        return incoming
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    override fun toString() = "ClassMappingData($names)"

}
