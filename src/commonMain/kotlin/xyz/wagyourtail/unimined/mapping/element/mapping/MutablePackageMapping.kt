package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.asMetadataDataList
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.toSmallMap
import xyz.wagyourtail.unimined.mapping.tree.impl.PackageMappingData

interface MutablePackageMapping : PackageMapping, MutableMetadataParent {

    fun setNames(names: Map<Namespace, PackageName>)

    companion object {
        operator fun invoke(
            names: Map<Namespace, PackageName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutablePackageMapping = PackageMappingData(names.toSmallMap(), metadata.asMetadataDataList())
    }

}
