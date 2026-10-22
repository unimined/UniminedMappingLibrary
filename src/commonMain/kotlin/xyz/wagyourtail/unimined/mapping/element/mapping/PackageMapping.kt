package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.*
interface PackageMapping : MappingElement, MetadataParent {

    val names: Map<Namespace, PackageName>

    companion object {
        operator fun invoke(
            names: Map<Namespace, PackageName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): PackageMapping = MutablePackageMapping(names, metadata)
    }

}
