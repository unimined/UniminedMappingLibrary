package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

import xyz.wagyourtail.unimined.mapping.jvms.Type

/**
 * DocSegment — a doc body is a sequence of these: plain text passthrough
 * and tag segments carrying a resolvable [DocRef].
 */
sealed interface DocSegment : Type {

    /** non-link text — emitted verbatim */
    data class Text(val text: String) : DocSegment {
        override fun accept(visitor: (Any) -> Boolean) {
            visitor(this)
        }

        override fun toString() = text
    }

    /** `{@link T label}` / `@see T` / `[T]` etc — [label] optional */
    data class TagSegment(
        val tag: DocTag,
        val ref: DocRef,
        val label: String? = null,
    ) : DocSegment {
        override fun accept(visitor: (Any) -> Boolean) {
            if (visitor(this)) {
                tag.let(visitor)
                ref.accept(visitor)
                label?.let(visitor)
            }
        }

        override fun toString() = buildString {
            append(tag.open).append(ref)
            label?.let { append(" ").append(it) }
            append(tag.close)
        }
    }
}
