package xyz.wagyourtail.unimined.mapping.formats.at

/**
 * AT file format differentiator. FML-era files join member to class
 * (`pkg/Class.member` / `pkg/Class.member(desc)`); modern files use
 * whitespace-separated class and member (`dot.Class member(desc)`).
 *
 * Detection scans the file for *unambiguous* signals — `(` or `/`
 * inside the second token is legacy-only; a whitespace member after
 * a dotted class token is modern-only — since both forms can emit
 * the ambiguous `a.b` shape (legacy member on a package-less class
 * vs modern class `a.b` in package `a`). The first unambiguous line
 * decides; a fully ambiguous file defaults to modern.
 */
object ATFormat {

    enum class Kind { LEGACY, MODERN }

    /** Classify an AT file's content lines. */
    fun detect(lines: Sequence<String>): Kind {
        for (l in lines) {
            val t = l.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val toks = t.split(Regex("\\s+"))
            if (toks.size < 2) continue
            val c = toks[1]
            // member desc joined onto the class token — legacy-only
            if ('(' in c || '/' in c) return Kind.LEGACY
            // whitespace member after the class token — modern-only;
            // class-only entries (2 tokens) stay ambiguous and keep
            // scanning
            if (toks.size > 2 && !toks[2].startsWith("#")) {
                return Kind.MODERN
            }
        }
        return Kind.MODERN
    }

    fun detect(content: String): Kind = detect(content.lineSequence())
}
