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
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableLocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.mergeMetadata
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toNsMap

@Serializable
data class MethodMappingData(
    override val entries: MutableMap<Namespace, MethodNameAndDescriptor> = mutableMapOf(),
    override val parameters: MutableList<ParameterMappingData> = mutableListOf(),
    override val localVariables: MutableList<LocalVariableMappingData> = mutableListOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableMethodMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null

    /**
     * mutation counter on [entries] — [MappingTreeData.pop] snapshots it at
     * push and skips its sibling re-merge scan when nothing merged into the
     * row during its subtree (entries are the only merge-candidacy state;
     * params/lvars/metadata don't affect it)
     */
    @Transient
    internal var mergeVersion = 0

    @Transient
    internal var pushedMergeVersion = -1

    // entry values are `name;desc` (or bare `name`) — checking the stored
    // string directly avoids materializing the `names` map (substring +
    // map per access) in merge loops that inspect every sibling
    val isClinit get() = entries.values.any {
        it.value == "<clinit>" || it.value.startsWith("<clinit>;")
    }
    val isInit get() = entries.values.any {
        it.value == "<init>" || it.value.startsWith("<init>;")
    }

    override fun setNames(names: Map<Namespace, UnqualifiedName>) {
        if (isClinit && names.values.any { it.value != "<clinit>" }) {
            throw IllegalStateException("clinit method name must be <clinit>")
        }
        if (isInit && names.values.any { it.value != "<init>" }) {
            throw IllegalStateException("init method name must be <init>")
        }
        for ((ns, name) in names) {
            entries[ns] = name.withMethodDesc(entries[ns]?.descriptor)
        }
        mergeVersion++
    }

    override fun setDescriptors(descs: Map<Namespace, MethodDescriptor>) {
        for ((ns, desc) in descs) {
            entries[ns]?.let { entries[ns] = it.name.withMethodDesc(desc) }
        }
        mergeVersion++
    }

    override fun setEntries(entries: Map<Namespace, MethodNameAndDescriptor>) {
        this.entries.putAll(entries)
        mergeVersion++
    }

    override fun parameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): MutableParameterMapping {
        val incoming = ParameterMappingData(index, lvOrd, names.toNsMap())
        for (existing in parameters) {
            if (incoming.mergeInto(existing)) return existing
        }
        parameters.add(incoming)
        return incoming
    }

    override fun localVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): MutableLocalVariableMapping {
        for (existing in localVariables) {
            if (existing.lvOrd == lvOrd && existing.startOp == startOp) {
                existing.setNames(names)
                return existing
            }
        }
        val node = LocalVariableMappingData(lvOrd, startOp, names.toNsMap())
        localVariables.add(node)
        return node
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
    internal fun descMergeable(existing: MethodMappingData): Boolean {
        if (isInit != existing.isInit || isClinit != existing.isClinit) return false
        if (entries.namesMatch(existing.entries) != NameMatch.NONE) return false
        if (entries.hasNameConflict(existing.entries)) return false
        if (!entries.anyDesc() || !existing.entries.anyDesc()) return false
        val descNs = existing.entries.pickDescNs(entries)
        return existing.entries.descMatches(entries, descNs, descRemapper)
    }

    /** merge this (incoming) into [existing]; ported from FieldMethodResolvable.merge */
    internal fun mergeInto(existing: MethodMappingData, descOnlyPermitted: () -> Boolean = { true }): Boolean {
        // a ctor/clinit row can never be the same member as a named
        // method — desc-only matching (`()V` etc.) would otherwise
        // collapse them into a mixed row that violates the init-name
        // invariant on the next setNames/fill
        if (isInit != existing.isInit || isClinit != existing.isClinit) return false
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

    private fun copyNamesFrom(incoming: Map<Namespace, MethodNameAndDescriptor>) {
        for ((ns, e) in incoming) {
            entries[ns] = e.name.withMethodDesc(entries[ns]?.descriptor)
        }
        mergeVersion++
    }

    private fun copyDescriptorsFrom(incoming: Map<Namespace, MethodNameAndDescriptor>) {
        for ((ns, e) in incoming) {
            val desc = e.descriptor ?: continue
            entries[ns]?.let { entries[ns] = it.name.withMethodDesc(desc) }
        }
        mergeVersion++
    }

    internal fun mergeChildrenInto(target: MethodMappingData) {
        for (p in parameters) target.parameter(p.index, p.lvOrd, p.names)
        for (l in localVariables) target.localVariable(l.lvOrd, l.startOp, l.names)
        for (m in metadata) target.addMetadata(m)
    }

    override fun toString() = "MethodMappingData($entries)"

}
