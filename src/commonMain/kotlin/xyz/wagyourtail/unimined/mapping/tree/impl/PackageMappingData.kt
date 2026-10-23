@file:UseSerializers(NamespaceSerializer::class, PackageNameSerializer::class)

package xyz.wagyourtail.unimined.mapping.tree.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutablePackageMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.mergeMetadata
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.serialization.NamespaceSerializer
import xyz.wagyourtail.unimined.mapping.serialization.PackageNameSerializer

@Serializable
data class PackageMappingData(
    override val names: MutableMap<Namespace, PackageName> = mutableMapOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutablePackageMapping {

    override fun setNames(names: Map<Namespace, PackageName>) {
        this.names.putAll(names)
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

}
