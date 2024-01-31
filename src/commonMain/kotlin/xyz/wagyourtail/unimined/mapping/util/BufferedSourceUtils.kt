package xyz.wagyourtail.unimined.mapping.util

import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use

enum class TokenType {
    STRING,
    LITERAL
}

fun BufferedSource.isZip(): Boolean {
    peek().use {
        try {
            val bs = it.readByteString(4)
            return bs == "PK\u0003\u0004".encodeUtf8() || bs == "PK\u0005\u0006".encodeUtf8()
        } catch (e: Exception) {
            return false
        }
    }
}

fun BufferedSource.takeWhitespace(): String {
    return buildString {
        while (!exhausted()) {
            if (peek().readUtf8CodePoint().checkedToChar()?.isWhitespace() != true) {
                break
            }
            appendCodePoint(readUtf8CodePoint())
        }
    }
}

fun BufferedSource.takeNext(): Pair<TokenType, String> {
    takeWhitespace()
    if (exhausted()) {
        return TokenType.LITERAL to ""
    }
    val next = peek().readUtf8CodePoint().checkedToChar()
    if (next == '"') {
        return TokenType.STRING to takeString().let {
            it.substring(1, it.length - 1).translateEscapes()
        }
    }
    return TokenType.LITERAL to takeUTF8Until { it.checkedToChar()?.isWhitespace() == true }
}

fun BufferedSource.takeRemainingOnLine(): List<Pair<TokenType, String>> {
    val list = mutableListOf<Pair<TokenType, String>>()
    while (!exhausted() && !peek().takeWhitespace().contains('\n')) {
        list.add(takeNext())
    }
    return list
}

fun BufferedSource.expect(c: Char): Char {
    val next = readUtf8CodePoint().checkedToChar()
    if (next != c) {
        throw IllegalArgumentException("Expected $c, found $next")
    }
    return next
}

fun BufferedSource.expect(s: String) = buildString {
    for (c in s) {
        append(expect(c))
    }
}

fun BufferedSource.takeLineAsBuffer(): BufferedSource {
    return Buffer().also {
        readUtf8Line()?.let { line ->
            it.writeUtf8(line)
        }
    }
}

inline fun BufferedSource.takeUTF8Until(condition: (Int) -> Boolean): String {
    return buildString {
        while (!exhausted()) {
            val c = peek().readUtf8CodePoint()
            if (condition(c)) {
                break
            }
            appendCodePoint(readUtf8CodePoint())
        }
    }
}

/**
 * reads an escaped string from the source
 * " characters "
 */
fun BufferedSource.takeString(): String {
    return buildString {
        var c = readUtf8CodePoint()
        if (c.checkedToChar() != '"') {
            throw IllegalArgumentException("Expected \", found ${c.checkedToChar()}")
        }
        appendCodePoint(c)
        var escapes = 0
        while (true) {
            c = readUtf8CodePoint()
            if (c == '"'.code && escapes == 0) {
                appendCodePoint(c)
                break
            }
            if (c == '\\'.code) {
                escapes++
            } else {
                escapes = 0
            }
            appendCodePoint(c)
            if (escapes == 2) {
                escapes = 0
            }
        }
    }
}