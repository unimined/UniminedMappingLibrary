package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion

/**
 * Javadoc:
 *   [DocSegment]* — a doc body is free text with embedded link tags
 *
 * Parses `{@link}` / `{@linkplain}` / `{@value}` / `@see` / `@throws` /
 * `@exception` / `@serialField` and KDoc `[ref]` links into
 * [DocSegment]s so callers can remap just the [DocRef]s (namespace-view
 * docs, source javadoc insertion, doc-comment remapping). Never throws —
 * unresolvable text stays [DocSegment.Text].
 */
data class Javadoc(val segments: List<DocSegment>) : Type {

    companion object : TypeCompanion<Javadoc> {

        override fun shouldRead(reader: CharReader<*>) = true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(read(reader))
        }

        override fun read(reader: CharReader<*>): Javadoc {
            val out = mutableListOf<DocSegment>()
            var text = StringBuilder()
            while (!reader.exhausted()) {
                val tag = tagAt(reader)
                if (tag != null) {
                    if (text.isNotEmpty()) { out += DocSegment.Text(text.toString()); text = StringBuilder() }
                    val seg = readTag(reader, tag)
                    if (seg != null) out += seg else text.append(tag.open)
                } else text.append(reader.take())
            }
            if (text.isNotEmpty()) out += DocSegment.Text(text.toString())
            return Javadoc(out)
        }

        override fun unchecked(value: String): Javadoc =
            StringCharReader(value).let { read(it) }

        fun of(text: String) = unchecked(text)

        private fun tagAt(reader: CharReader<*>): DocTag? {
            val start = when (reader.peek()) {
                '[' -> return DocTag.KDOC
                '{' -> "{@"
                '@' -> "@"
                else -> return null
            }
            return DocTag.entries.firstOrNull {
                it != DocTag.KDOC && it.open.startsWith(start) && ahead(reader, it.open)
            }
        }

        private fun ahead(reader: CharReader<*>, s: String): Boolean {
            val c = reader.copy()
            repeat(s.length) { if (c.take() != s[it]) return false }
            return true
        }

        private fun readTag(reader: CharReader<*>, tag: DocTag): DocSegment? {
            reader.expect(tag.open)
            val body = when (tag) {
                DocTag.KDOC -> reader.takeUntil { it == ']' }.also { if (!reader.exhausted()) reader.expect(']') }
                DocTag.LINK, DocTag.LINKPLAIN, DocTag.VALUE ->
                    reader.takeUntil { it == '}' }.also { if (!reader.exhausted()) reader.expect('}') }
                else -> reader.takeUntil { it == '\n' || it == '\r' }
            }
            val (refText, label) = splitLabel(body)
            val trimmed = refText.trim()
            // quoted/`html` @see forms aren't refs — stay text
            if (trimmed.isEmpty() || (!trimmed[0].isLetterOrDigit() && trimmed[0] != '#')) return null
            val ref = runCatching { DocRef.unchecked(trimmed) }.getOrNull() ?: return null
            return DocSegment.TagSegment(tag, ref, label?.takeIf { it.isNotEmpty() })
        }

        private fun splitLabel(body: String): Pair<String, String?> {
            if (body.isEmpty()) return "" to null
            val pipe = body.indexOf('|')
            if (pipe >= 0) return body.substring(0, pipe) to body.substring(pipe + 1)
            // first whitespace *outside* parens — `m(int, x.Iface) label`
            // keeps its params in the ref
            var depth = 0
            for (i in body.indices) {
                when (body[i]) {
                    '(' -> depth++
                    ')' -> depth--
                    ' ', '\t' -> if (depth <= 0) {
                        return body.substring(0, i) to body.substring(i).trimStart()
                    }
                }
            }
            return body to null
        }
    }

    /** [transform] every link target — doc text otherwise untouched */
    fun remap(transform: (DocRef) -> DocRef): Javadoc =
        copy(segments = segments.map {
            if (it is DocSegment.TagSegment) it.copy(ref = transform(it.ref)) else it
        })

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) segments.forEach { it.accept(visitor) }
    }

    override fun toString(): String = segments.joinToString("") { it.toString() }
}
