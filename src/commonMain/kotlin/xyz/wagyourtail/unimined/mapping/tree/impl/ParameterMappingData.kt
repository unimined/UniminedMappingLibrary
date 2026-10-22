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
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class ParameterMappingData(
    override var index: Int? = null,
    override var lvOrd: Int? = null,
    override val names: MutableMap<Namespace, UnqualifiedName> = mutableMapOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableParameterMapping {

    override fun setNames(names: Map<Namespace, UnqualifiedName>) {
        this.names.putAll(names)
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    /** ported from ParameterMappingImpl.merge */
    internal fun mergeInto(existing: ParameterMappingData): Boolean {
        if (existing.index == null && existing.lvOrd == null) {
            existing.index = index
            existing.lvOrd = lvOrd
            doMerge(existing)
            return true
        }
        if (existing.index != null && existing.index == index) {
            if (existing.lvOrd != null && lvOrd != null && lvOrd != existing.lvOrd) {
                return false
            }
            if (lvOrd != null) existing.lvOrd = lvOrd
            doMerge(existing)
            return true
        }
        if (existing.lvOrd != null && existing.lvOrd == lvOrd) {
            if (index != null) existing.index = index
            doMerge(existing)
            return true
        }
        return false
    }

    private fun doMerge(target: ParameterMappingData) {
        target.setNames(names)
        for (m in metadata) target.addMetadata(m)
    }

}
