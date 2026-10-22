package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.MetadataElement

interface MetadataParent {

    val metadata: Iterable<MetadataElement>
        get() = emptyList()

}
