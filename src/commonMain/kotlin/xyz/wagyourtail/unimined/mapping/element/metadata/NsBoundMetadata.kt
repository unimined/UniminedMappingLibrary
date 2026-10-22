package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement

/**
 * metadata whose name-typed values are relative to a namespace.
 * the association is impl-internal bookkeeping, rewritten by ns-remap wrappers.
 */
sealed interface NsBoundMetadata : MetadataElement {

    val baseNs: Namespace

}
