package xyz.wagyourtail.unimined.mapping.sink

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.Element
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement

/**
 * the only streaming interface left: format readers push elements into it,
 * tree impls/format writers/lazy impls consume it.
 *
 * push an element head, push its children, then [pop] when the element is complete.
 * metadata elements are leaves — [push] on them is self-contained (no [pop]).
 */
interface MappingSink {

    fun header(namespaces: List<Namespace>) {}

    /**
     * open/attach an element under the current open element (or the root).
     * @return the retained element (may differ from [element] if merged), or null if this
     *         sink wants the element's subtree skipped (replaces nullable visit*)
     */
    fun push(element: MappingElement): MappingElement?

    /**
     * attach metadata to the current open element.
     * @return the metadata element if accepted, null to skip it (and its children)
     */
    fun push(meta: MetadataElement): MetadataElement?

    /**
     * close the given element — implementations should drop it (and any un-closed
     * elements above it) from their open-element tracking.
     */
    fun pop(element: MappingElement)

    /** close a metadata element — only needed when the sink keeps metas open (children) */
    fun pop(meta: MetadataElement) {}

    /** dispatch close for mixed mapping/metadata element stacks */
    fun pop(element: Element) {
        when (element) {
            is MappingElement -> pop(element)
            is MetadataElement -> pop(element)
        }
    }

    fun end() {}

}

/** push element, run [block] for its children, then pop — mirrors the old `?.use {}` flow */
inline fun MappingSink.pushed(element: MappingElement, block: () -> Unit) {
    val retained = push(element)
    if (retained != null) {
        block()
        pop(retained)
    }
}

/** same, for metadata that can itself contain metadata (e.g. inner-class access) */
inline fun MappingSink.pushed(meta: MetadataElement, block: () -> Unit) {
    val retained = push(meta)
    if (retained != null) {
        block()
        pop(retained)
    }
}
