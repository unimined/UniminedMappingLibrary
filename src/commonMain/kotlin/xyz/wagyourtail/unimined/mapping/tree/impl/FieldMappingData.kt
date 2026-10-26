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
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.mergeMetadata
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
        val matched = entries.namesMatch(existing.entries)
        if (matched == NameMatch.NONE) {
            // disjoint namespace keys are still the same member when descriptors verify
            if (entries.hasNameConflict(existing.entries)) return false
            if (entries.anyDesc() && existing.entries.anyDesc()) {
                val descNs = existing.entries.pickDescNs(entries)
                if (existing.entries.descMatches(entries, descNs, descRemapper)) {
                    existing.copyNamesFrom(entries)
                    existing.copyDescriptorsFrom(entries)
                    mergeChildrenInto(existing)
                    return true
                }
            }
            return false
        }
        if (existing.entries.anyDesc()) {
            if (entries.anyDesc()) {
                val descNs = existing.entries.pickDescNs(entries)
                if (existing.entries.descMatches(entries, descNs, descRemapper)) {
                    if (matched == NameMatch.PARTIAL) logPartialMerge(this, existing)
                    existing.copyNamesFrom(entries)
                    existing.copyDescriptorsFrom(entries)
                    mergeChildrenInto(existing)
                    return true
                }
                return false
            }
            mergeDescMismatch(existing, this)
            return false
        }
        if (entries.anyDesc()) {
            mergeDescMismatch(this, existing)
            return false
        }
        if (matched == NameMatch.PARTIAL) logPartialMerge(this, existing)
        existing.copyNamesFrom(entries)
        mergeChildrenInto(existing)
        return true
    }

    private fun copyNamesFrom(incoming: Map<Namespace, FieldNameAndDescriptor>) {
        for ((ns, e) in incoming) {
            entries[ns] = e.name.withFieldDesc(entries[ns]?.descriptor)
        }
    }

    private fun copyDescriptorsFrom(incoming: Map<Namespace, FieldNameAndDescriptor>) {
        for ((ns, e) in incoming) {
            val desc = e.descriptor ?: continue
            entries[ns]?.let { entries[ns] = it.name.withFieldDesc(desc) }
        }
    }

    internal fun mergeChildrenInto(target: FieldMappingData) {
        for (m in metadata) target.addMetadata(m)
    }

    override fun toString() = "FieldMappingData($entries)"

}
