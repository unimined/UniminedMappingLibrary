package xyz.wagyourtail.unimined.mapping.element.metadata

interface InvokableMetadataParent : AccessParentMapping {

    val exceptions: List<ExceptionElement>
        get() = metadata.filterIsInstance<ExceptionElement>()

}
