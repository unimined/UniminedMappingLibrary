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
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class MethodMappingData(
    override val entries: MutableMap<Namespace, MethodNameAndDescriptor> = mutableMapOf(),
    override val parameters: MutableList<ParameterMappingData> = mutableListOf(),
    override val localVariables: MutableList<LocalVariableMappingData> = mutableListOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableMethodMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null

    val isClinit get() = names.values.any { it.value == "<clinit>" }
    val isInit get() = names.values.any { it.value == "<init>" }

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
    }

    override fun setDescriptors(descs: Map<Namespace, MethodDescriptor>) {
        for ((ns, desc) in descs) {
            entries[ns]?.let { entries[ns] = it.name.withMethodDesc(desc) }
        }
    }

    override fun setEntries(entries: Map<Namespace, MethodNameAndDescriptor>) {
        this.entries.putAll(entries)
    }

    override fun parameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): MutableParameterMapping {
        val incoming = ParameterMappingData(index, lvOrd, names.toMutableMap())
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
        val node = LocalVariableMappingData(lvOrd, startOp, names.toMutableMap())
        localVariables.add(node)
        return node
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    /** merge this (incoming) into [existing]; ported from FieldMethodResolvable.merge */
    internal fun mergeInto(existing: MethodMappingData): Boolean {
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

    internal fun mergeChildrenInto(target: MethodMappingData) {
        for (p in parameters) target.parameter(p.index, p.lvOrd, p.names)
        for (l in localVariables) target.localVariable(l.lvOrd, l.startOp, l.names)
        for (m in metadata) target.addMetadata(m)
    }

    override fun toString() = "MethodMappingData($entries)"

}
