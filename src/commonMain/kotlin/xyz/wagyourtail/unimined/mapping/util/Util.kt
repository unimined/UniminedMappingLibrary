package xyz.wagyourtail.unimined.mapping.util

import okio.Buffer
import okio.BufferedSource
import okio.use

fun BufferedSource.takeUTF8Until(condition: (Int) -> Boolean): String {
    return Buffer().use {
        while (!exhausted()) {
            val c = peek().readUtf8CodePoint()
            if (condition(c)) {
                break
            }
            it.writeUtf8CodePoint(readUtf8CodePoint())
        }
        it.readUtf8()
    }
}

/**
 * reads an escaped string from the source
 * " characters "
 */
fun BufferedSource.takeString(): String {
    return Buffer().use {
        val c = peek().readUtf8CodePoint()
        if (c != '"'.code) {
            throw IllegalArgumentException("Invalid string start: ${c.toUnicode()}")
        }
        it.writeUtf8CodePoint(readUtf8CodePoint())
        var escapes = 0
        while (true) {
            val c = readUtf8CodePoint()
            if (c == '"'.code && escapes == 0) {
                it.writeUtf8CodePoint(c)
                break
            }
            if (c == '\\'.code) {
                escapes++
            } else {
                escapes = 0
            }
            it.writeUtf8CodePoint(c)
            if (escapes == 2) {
                escapes = 0
            }
        }
        it.readUtf8()
    }
}

fun <E, K, V> Iterable<E>.associateNonNull(apply: (E) -> Pair<K, V>?): Map<K, V> {
    val mut = mutableMapOf<K, V>()
    for (e in this) {
        apply(e)?.let {
            mut.put(it.first, it.second)
        }
    }
    return mut
}