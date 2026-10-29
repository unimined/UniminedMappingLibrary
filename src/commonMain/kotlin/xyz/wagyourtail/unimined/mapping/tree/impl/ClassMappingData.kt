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
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableWildcardMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.mergeMetadata
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toNsMap

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
        val incoming = MethodMappingData(entries.toNsMap())
        incoming.descRemapper = descRemapper
        // name-keyed merges are unambiguous and always win — desc-only
        // matching is a fallback that must not outrun a name match later
        // in the list (record accessors hit this: hashCode's ()I desc
        // matches the searchDistance accessor's, but the accessor's own
        // row already carries the shared intermediary name)
        // snapshot before any merge attempts — push-time desc-mismatch
        // splices mutate entries and bump mergeVersion, so a bumped row at
        // pop means "revisit siblings" not "unchanged"
        incoming.pushedMergeVersion = incoming.mergeVersion
        for (existing in methods) {
            if (incoming.mergeInto(existing) { false }) return existing
        }
        // desc-only merges additionally require a unique candidate — with
        // several same-descriptor siblings the first match wins by
        // iteration order and cross-wires names (unobfuscated members
        // never seen by one source, e.g. forge's
        // Schema#registerEntities/registerBlockEntities)
        val unique = methods.count { incoming.descMergeable(it) } == 1
        for (existing in methods) {
            if (incoming.mergeInto(existing) { unique }) return existing
        }
        methods.add(incoming)
        return incoming
    }

    override fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping {
        val incoming = FieldMappingData(entries.toNsMap())
        incoming.descRemapper = descRemapper
        // see method(): name-keyed merges first, unique desc-only second
        incoming.pushedMergeVersion = incoming.mergeVersion
        for (existing in fields) {
            if (incoming.mergeInto(existing) { false }) return existing
        }
        val unique = fields.count { incoming.descMergeable(it) } == 1
        for (existing in fields) {
            if (incoming.mergeInto(existing) { unique }) return existing
        }
        fields.add(incoming)
        return incoming
    }

    override fun wildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): MutableWildcardMapping {
        val incoming = WildcardMappingData(type, descs.toNsMap())
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
