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
import xyz.wagyourtail.unimined.mapping.forEachNsEntry
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

    /** see [MethodMappingData.mergeVersion] */
    @Transient
    internal var mergeVersion = 0

    @Transient
    internal var pushedMergeVersion = -1

    /** see [MethodMappingData.detached] */
    @Transient
    internal var detached = false

    override fun setNames(names: Map<Namespace, UnqualifiedName>) {
        names.forEachNsEntry { ns, name ->
            entries[ns] = name.withFieldDesc(entries[ns]?.descriptor)
        }
        mergeVersion++
    }

    override fun setDescriptors(descs: Map<Namespace, FieldDescriptor>) {
        descs.forEachNsEntry { ns, desc ->
            entries[ns]?.let { entries[ns] = it.name.withFieldDesc(desc) }
        }
        mergeVersion++
    }

    override fun setEntries(entries: Map<Namespace, FieldNameAndDescriptor>) {
        this.entries.putAll(entries)
        mergeVersion++
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    /**
     * pure check for the disjoint-namespace-keys branch of [mergeInto]:
     * true when this row and [existing] share no name keys, have no name
     * conflict, and verify the same descriptor. callers use it to count
     * candidate siblings before permitting a desc-only merge.
     */
    internal fun descMergeable(existing: FieldMappingData): Boolean {
        if (entries.namesMatch(existing.entries) != NameMatch.NONE) return false
        if (entries.hasNameConflict(existing.entries)) return false
        if (!entries.anyDesc() || !existing.entries.anyDesc()) return false
        val descNs = existing.entries.pickDescNs(entries)
        return existing.entries.descMatches(entries, descNs, descRemapper)
    }

    internal fun mergeInto(existing: FieldMappingData, descOnlyPermitted: () -> Boolean = { true }): Boolean {
        val matched = entries.namesMatch(existing.entries)
        if (matched == NameMatch.NONE) {
            // disjoint namespace keys are still the same member when descriptors verify
            if (!descOnlyPermitted()) return false
            if (descMergeable(existing)) {
                existing.copyNamesFrom(entries)
                existing.copyDescriptorsFrom(entries)
                mergeChildrenInto(existing)
                return true
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
        // see MethodMappingData — skip no-op puts to avoid entry allocs
        // and spurious mergeVersion bumps
        incoming.forEachNsEntry { ns, e ->
            val cur = entries[ns]
            val next = e.name.withFieldDesc(cur?.descriptor)
            if (next == cur) return@forEachNsEntry
            entries[ns] = next
            mergeVersion++
        }
    }

    private fun copyDescriptorsFrom(incoming: Map<Namespace, FieldNameAndDescriptor>) {
        incoming.forEachNsEntry { ns, e ->
            val desc = e.descriptor ?: return@forEachNsEntry
            val cur = entries[ns] ?: return@forEachNsEntry
            if (cur.descriptor == desc) return@forEachNsEntry
            entries[ns] = cur.name.withFieldDesc(desc)
            mergeVersion++
        }
    }

    internal fun mergeChildrenInto(target: FieldMappingData) {
        for (m in metadata) target.addMetadata(m)
    }

    override fun toString() = "FieldMappingData($entries)"

}
