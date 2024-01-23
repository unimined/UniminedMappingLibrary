package xyz.wagyourtail.unimined.mapping.util

class CharReader(val buffer: String, var pos: Int = 0) {

    fun copy() = CharReader(buffer, pos)

    fun exhausted() = pos >= buffer.length

    fun peek(): Char? {
        if (pos >= buffer.length) return null
        return buffer[pos]
    }

    fun take(): Char? {
        if (pos >= buffer.length) return null
        return buffer[pos++]
    }

    fun takeLine(): String {
        return takeUntil { it == '\n' }
    }

    inline fun takeUntil(predicate: (Char) -> Boolean): String {
        return buildString {
            while (pos < buffer.length && !predicate(buffer[pos])) {
                append(buffer[pos++])
            }
        }
    }

    inline fun takeWhile(predicate: (Char) -> Boolean): String {
        return buildString {
            while (pos < buffer.length && predicate(buffer[pos])) {
                append(buffer[pos++])
            }
        }
    }

    fun takeWhitespace(): String {
        return takeUntil { !it.isWhitespace() }
    }

    fun takeNext(sep: (Char) -> Boolean = { it.isWhitespace() }): Pair<TokenType, String> {
        takeWhile(sep)
        if (pos >= buffer.length) {
            return TokenType.LITERAL to ""
        }
        val next = peek()
        if (next == '"') {
            return TokenType.STRING to takeString().let {
                it.substring(1, it.length - 1).translateEscapes()
            }
        }
        return TokenType.LITERAL to takeUntil(sep)
    }

    fun takeNextLiteral(sep: Char = '\t'): String? {
        if (exhausted()) return null
        if (peek() == '\n') {
            return null
        }
        return buildString {
            while (!exhausted()) {
                val b = peek()
                if (b == '\n') break
                val c = take()
                if (c == sep) break
                append(c)
            }
        }
    }

    fun takeNonNewlineWhitespace(): String {
        return takeUntil { !it.isWhitespace() || it == '\n' }
    }

    fun takeRemainingOnLine(): List<Pair<TokenType, String>> {
        val list = mutableListOf<Pair<TokenType, String>>()
        while (pos < buffer.length) {
            takeNonNewlineWhitespace()
            val next = peek()
            if (next == '\n') {
                break
            }
            list.add(takeNext())
        }
        return list
    }

    fun takeString() = buildString {
        expect('"')
        append('"')
        var escapes = 0
        while (pos < buffer.length) {
            val c = take()
            if (c == '"' && escapes == 0) {
                append(c)
                break
            }
            if (c == '\\') {
                escapes++
            } else {
                escapes = 0
            }
            append(c)
            if (escapes == 2) {
                escapes = 0
            }
        }
    }

    fun expect(c: Char): Char {
        val next = take()
        if (next != c) {
            throw IllegalArgumentException("Expected $c, found $next")
        }
        return next
    }

    inline fun <T> use(block: (CharReader) -> T) = let(block)

}