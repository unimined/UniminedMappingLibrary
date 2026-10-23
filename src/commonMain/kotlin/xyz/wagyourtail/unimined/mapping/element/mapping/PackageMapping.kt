package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName

interface PackageMapping : MappingElement, MetadataParent {

    val names: Map<Namespace, PackageName>

    companion object {
        operator fun invoke(
            names: Map<Namespace, PackageName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): PackageMapping = MutablePackageMapping(names, metadata)
    }

}
