package xyz.wagyourtail.unimined.mapping.formats.at

/**
 * AT file format differentiator. FML-era files join member to class
 * (`pkg/Class.member` / `pkg/Class.member(desc)`); modern files use
 * whitespace-separated class and member (`dot.Class member(desc)`).
 *
 * Detection scans the file for *unambiguous* signals — a whitespace
 * member after the class token is modern-only (legacy members are
 * always dot-joined onto the class token, so a bare third token can
 * never be legacy); `(` or a `.`-joined member inside the second
 * token is legacy-only. A bare internal-name class token (`a/b/C`)
 * is *ambiguous* — class-only entries are spelled identically in
 * both formats — so it decides nothing; both forms can also emit
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
            // whitespace member after the class token — modern-only
            if (toks.size > 2 && !toks[2].startsWith("#")) {
                return Kind.MODERN
            }
            // desc joined onto the class token (`a/b/C.m()V`), or an
            // internal name carrying a `.`-joined member (`a/b/C.f`)
            // — legacy-only. A bare internal-name class token is
            // ambiguous (class-only entries exist in both formats)
            // and keeps scanning.
            if ('(' in c || ('/' in c && '.' in c)) return Kind.LEGACY
        }
        return Kind.MODERN
    }

    fun detect(content: String): Kind = detect(content.lineSequence())
}
