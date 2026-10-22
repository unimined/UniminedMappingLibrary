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
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toSmallMap

@Serializable
data class WildcardMappingData(
    override val type: WildcardType,
    override val descs: MutableMap<Namespace, FieldOrMethodDescriptor> = mutableMapOf(),
    override val parameters: MutableList<ParameterMappingData> = mutableListOf(),
    override val localVariables: MutableList<LocalVariableMappingData> = mutableListOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableWildcardMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null

    override fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>) {
        this.descs.putAll(descs)
    }

    override fun parameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): MutableParameterMapping {
        val incoming = ParameterMappingData(index, lvOrd, names.toSmallMap())
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
        val node = LocalVariableMappingData(lvOrd, startOp, names.toSmallMap())
        localVariables.add(node)
        return node
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    internal fun mergeInto(existing: WildcardMappingData): Boolean {
        if (existing.type != type) return false
        if (existing.descs.isEmpty() && descs.isEmpty()) {
            mergeChildrenInto(existing)
            return true
        }
        if (existing.descs.isNotEmpty() && descs.isNotEmpty()) {
            val descNs = existing.descs.keys.intersect(descs.keys).firstOrNull() ?: descs.keys.first()
            if (descIn(existing.descs, descNs, descRemapper) == descs[descNs]) {
                existing.setDescriptors(descs)
                mergeChildrenInto(existing)
                return true
            }
        }
        return false
    }

    internal fun mergeChildrenInto(target: WildcardMappingData) {
        for (p in parameters) target.parameter(p.index, p.lvOrd, p.names)
        for (l in localVariables) target.localVariable(l.lvOrd, l.startOp, l.names)
        for (m in metadata) target.addMetadata(m)
    }

}
