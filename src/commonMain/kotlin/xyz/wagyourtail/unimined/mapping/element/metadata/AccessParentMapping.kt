package xyz.wagyourtail.unimined.mapping.element.metadata

interface AccessParentMapping : SignatureParentMapping {

    val access: List<AccessElement>
        get() = metadata.filterIsInstance<AccessElement>()

}
