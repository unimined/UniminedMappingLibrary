package xyz.wagyourtail.unimined.mapping.element.metadata

interface AnnotationParentMapping : JavadocParentMapping {

    val annotations: List<AnnotationElement>
        get() = metadata.filterIsInstance<AnnotationElement>()

}
