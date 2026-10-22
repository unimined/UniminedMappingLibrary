package xyz.wagyourtail.unimined.mapping.element.metadata

interface SignatureParentMapping : JavadocParentMapping {

    val signature: SignatureElement?
        get() = metadata.filterIsInstance<SignatureElement>().firstOrNull()

}
