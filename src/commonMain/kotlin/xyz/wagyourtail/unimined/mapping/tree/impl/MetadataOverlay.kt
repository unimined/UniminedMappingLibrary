package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AnnotationElement
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent

/**
 * layers metadata-only data — javadocs and annotations — onto this tree:
 * overlay rows are matched to tree rows by the overlay's source
 * namespace, and matching rows receive the overlay's
 * [JavadocElement]/[AnnotationElement] metadata they don't already
 * carry. This is the "annotation/javadoc layers" merge — e.g.
 * mod-provided javadocs merge comments into the mapping bundle without
 * touching names.
 *
 * Name cells and descs are never modified; unmatched overlay rows are
 * skipped. Applied overlays stack: each overlay adds metadata earlier
 * ones didn't supply (no overwrite: rows keep the first
 * javadoc/annotation value they got).
 *
 * @param overlay the tree supplying metadata — its rows are keyed by
 *  [srcNs]
 * @param srcNs the overlay column rows match on — defaults to the
 *  overlay's first namespace, the convention for authored overlay data
 * @return `false` when [srcNs] could not be resolved or is not a
 *  namespace of this tree (the overlay is skipped — callers log);
 *  `true` once the overlay's rows were merged
 */
fun MutableMappingTree.mergeMetadataOverlay(
    overlay: MappingTree,
    srcNs: Namespace? = overlay.namespaces.firstOrNull()
): Boolean {
    if (srcNs == null || srcNs !in namespaces) return false
    for (overlayCls in overlay.classes) {
        val name = overlayCls.names[srcNs] ?: continue
        val target = getClass(srcNs, name) as? MutableClassMapping ?: continue
        target.mergeMetadata(overlayCls.metadata)
        for (overlayMethod in overlayCls.methods) {
            val entry = overlayMethod.entries[srcNs] ?: continue
            val targetMethod = target.methods
                .filterIsInstance<MutableMethodMapping>()
                .firstOrNull {
                    val e = it.entries[srcNs] ?: return@firstOrNull false
                    e.name == entry.name && e.descriptor == entry.descriptor
                } ?: continue
            targetMethod.mergeMetadata(overlayMethod.metadata)
            val targetParams = targetMethod.parameters.toList()
            for (overlayParam in overlayMethod.parameters) {
                val targetParam = targetParams.firstOrNull {
                    it.index == overlayParam.index && it.lvOrd == overlayParam.lvOrd
                } as? MutableParameterMapping ?: continue
                targetParam.mergeMetadata(overlayParam.metadata)
            }
        }
        for (overlayField in overlayCls.fields) {
            val entry = overlayField.entries[srcNs] ?: continue
            val targetField = target.fields
                .filterIsInstance<MutableFieldMapping>()
                .firstOrNull {
                    val e = it.entries[srcNs] ?: return@firstOrNull false
                    e.name == entry.name && e.descriptor == entry.descriptor
                } ?: continue
            targetField.mergeMetadata(overlayField.metadata)
        }
    }
    return true
}

/**
 * Copy overlay [JavadocElement]/[AnnotationElement] the row lacks:
 * a row with a javadoc already keeps it (first layer wins — a second
 * doc element would render as duplicate comments), while annotations
 * merge by value since several distinct annotations are legitimate.
 */
private fun MutableMetadataParent.mergeMetadata(overlayMeta: Iterable<MetadataElement>) {
    var hasJavadoc = metadata.any { it is JavadocElement }
    val annotations = metadata.filterIsInstance<AnnotationElement>()
        .mapTo(mutableSetOf()) { it.annotation.toString() }
    for (meta in overlayMeta) {
        when (meta) {
            is JavadocElement -> if (!hasJavadoc) {
                addMetadata(meta); hasJavadoc = true
            }

            is AnnotationElement -> if (annotations.add(meta.annotation.toString())) {
                addMetadata(meta)
            }

            else -> {}
        }
    }
}
