package xyz.wagyourtail.unimined.mapping.util

class CharReader(val buffer: CharArray, var pos: Int = 0) {

    constructor(input: String, pos: Int = 0): this(input.toCharArray(), pos)

    fun copy() = CharReader(buffer, pos)

    fun exhausted() = pos >= buffer.size

    fun peek(): Char? {
        if (pos >= buffer.size) return null
        return buffer[pos]
    }

    fun take(): Char? {
        if (pos > buffer.size) return null
        return buffer[pos++]
    }

    inline fun takeUntil(predicate: (Char) -> Boolean): String {
        return buildString {
            while (pos < buffer.size && !predicate(buffer[pos])) {
                append(buffer[pos++])
            }
        }
    }

    fun takeWhitespace(): String {
        return takeUntil { !it.isWhitespace() }
    }

    fun takeNext(): Pair<TokenType, String> {
        takeWhitespace()
        if (pos >= buffer.size) {
            return TokenType.LITERAL to ""
        }
        val next = peek()
        if (next == '"') {
            return TokenType.STRING to takeString().let {
                it.substring(1, it.length - 1).translateEscapes()
            }
        }
        return TokenType.LITERAL to takeUntil { it.isWhitespace() }
    }

    fun takeNonNewlineWhitespace(): String {
        return takeUntil { !it.isWhitespace() || it == '\n' }
    }

    fun takeRemainingOnLine(): List<Pair<TokenType, String>> {
        val list = mutableListOf<Pair<TokenType, String>>()
        while (pos < buffer.size) {
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
        while (pos < buffer.size) {
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