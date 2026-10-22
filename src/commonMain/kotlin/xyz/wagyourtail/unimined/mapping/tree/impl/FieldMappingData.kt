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
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class FieldMappingData(
    override val entries: MutableMap<Namespace, FieldNameAndDescriptor> = mutableMapOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableFieldMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null

    override fun setNames(names: Map<Namespace, UnqualifiedName>) {
        for ((ns, name) in names) {
            entries[ns] = name.withFieldDesc(entries[ns]?.descriptor)
        }
    }

    override fun setDescriptors(descs: Map<Namespace, FieldDescriptor>) {
        for ((ns, desc) in descs) {
            entries[ns]?.let { entries[ns] = it.name.withFieldDesc(desc) }
        }
    }

    override fun setEntries(entries: Map<Namespace, FieldNameAndDescriptor>) {
        this.entries.putAll(entries)
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    internal fun mergeInto(existing: FieldMappingData): Boolean {
        val matched = namesMatch(names, existing.names)
        if (matched == NameMatch.NONE) {
            // disjoint namespace keys are still the same member when descriptors verify
            if (hasNameConflict(names, existing.names)) return false
            if (descs.isNotEmpty() && existing.hasDescriptor()) {
                val descNs = existing.descs.keys.intersect(descs.keys).firstOrNull() ?: descs.keys.first()
                if (descIn(existing.descs, descNs, descRemapper) == descs[descNs]) {
                    existing.setNames(names)
                    existing.setDescriptors(descs)
                    mergeChildrenInto(existing)
                    return true
                }
            }
            return false
        }
        if (existing.hasDescriptor()) {
            if (descs.isNotEmpty()) {
                val descNs = existing.descs.keys.intersect(descs.keys).firstOrNull() ?: descs.keys.first()
                if (descIn(existing.descs, descNs, descRemapper) == descs[descNs]) {
                    if (matched == NameMatch.PARTIAL) logPartialMerge(this, existing)
                    existing.setNames(names)
                    existing.setDescriptors(descs)
                    mergeChildrenInto(existing)
                    return true
                }
                return false
            }
            mergeDescMismatch(existing, this)
            return false
        }
        if (descs.isNotEmpty()) {
            mergeDescMismatch(this, existing)
            return false
        }
        if (matched == NameMatch.PARTIAL) logPartialMerge(this, existing)
        existing.setNames(names)
        mergeChildrenInto(existing)
        return true
    }

    internal fun mergeChildrenInto(target: FieldMappingData) {
        for (m in metadata) target.addMetadata(m)
    }

    override fun toString() = "FieldMappingData($entries)"

}
