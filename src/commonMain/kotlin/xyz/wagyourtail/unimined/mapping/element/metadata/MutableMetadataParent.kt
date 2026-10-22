package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.MetadataElement

interface MutableMetadataParent : MetadataParent {

    fun addMetadata(meta: MetadataElement)

}
