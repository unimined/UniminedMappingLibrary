package xyz.wagyourtail.unimined.mapping.element.metadata

interface JavadocParentMapping : MetadataParent {

    val javadocs: List<JavadocElement>
        get() = metadata.filterIsInstance<JavadocElement>()

}
