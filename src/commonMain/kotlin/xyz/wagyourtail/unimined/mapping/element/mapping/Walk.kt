package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent

/**
 * Depth-first traversal of every [MappingElement] in the subtree, self first.
 */
fun MappingElement.walkMappings(): Sequence<MappingElement> =
    sequenceOf(this) + contents.asSequence().flatMap { it.walkMappings() }

/**
 * Depth-first traversal yielding `(owning parent, metadata)` pairs.
 *
 * The pair matters for insertion workflows — a bare [MetadataElement] lacks the
 * context of *where* it attaches (which method's javadoc, which class's signature).
 * For remapped-source insertion (jar → decompile → insert), filter/transform the
 * owner's names and [xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata.baseNs].
 */
fun MappingElement.walkMetadataPairs(): Sequence<Pair<MetadataParent, MetadataElement>> = sequence {
    (this@walkMetadataPairs as? MetadataParent)?.let { owner ->
        for (m in owner.metadata) yield(owner to m)
    }
    for (c in contents) yieldAll(c.walkMetadataPairs())
}

/**
 * Just the metadata, depth-first — [walkMetadataPairs] without the owner context.
 */
fun MappingElement.walkMetadata(): Sequence<MetadataElement> =
    walkMetadataPairs().map { it.second }
