package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.PackageMappingData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.toSmallMap

interface MutablePackageMapping : PackageMapping, MutableMetadataParent {

    fun setNames(names: Map<Namespace, PackageName>)

    companion object {
        operator fun invoke(
            names: Map<Namespace, PackageName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutablePackageMapping = PackageMappingData(names.toSmallMap(), metadata.asMetadataDataList())
    }

}
