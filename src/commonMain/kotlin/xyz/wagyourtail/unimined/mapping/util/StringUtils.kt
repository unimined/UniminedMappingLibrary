package xyz.wagyourtail.unimined.mapping.util

/**
 * unlike java, this will translate unicode escapes as well
 */
fun String.translateEscapes(): String {
    if (this.isEmpty() || !this.contains("\\")) {
        return this
    }
    return buildString {
        var i = 0
        while (i < this@translateEscapes.length) {
            val c = this@translateEscapes[i++]
            if (c == '\\') {
                if (i >= this@translateEscapes.length) throw IllegalArgumentException("Invalid escape, hit end of string")
                when (val n = this@translateEscapes[i++]) {
                    '"' -> append('"')
                    '\'' -> append('\'')
                    '\\' -> append('\\')
                    '0', '1', '2', '3', '4', '5', '6', '7' -> {
                        var octal = n.toString()
                        val max = if (octal.toInt() < 4) 2 else 1
                        for (j in 0 until max) {
                            if (i >= this@translateEscapes.length) break
                            val next = this@translateEscapes[i]
                            if (next !in ('0'..'7')) break
                            octal += next
                            i++
                        }
                        append(octal.toInt(8).toChar())
                    }
                    'u' -> {
                        val hex = this@translateEscapes.substring(i, i + 4)
                        if (hex.length != 4) throw IllegalArgumentException("Invalid unicode escape: $hex, expected 4 characters, found EOS")
                        append(hex.toInt(16).toChar())
                        i += 4
                    }
                    'b' -> append('\b')
                    'f' -> append('\u000C')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    's' -> append(' ')
                    't' -> append('\t')
                    else -> throw IllegalArgumentException("Invalid escape: $n")
                }
            } else {
                append(c)
            }
        }
    }
}

fun String.escape(unicode: Boolean = false, spaces: Boolean = false): String {
    if (this.isEmpty()) return this
    return buildString {
        var i = 0
        while (i < this@escape.length) {
            when (val c = this@escape[i++]) {
                '"' -> append("\\\"")
                '\'' -> append("\\'")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                ' ' -> {
                    if (spaces) {
                        append("\\s")
                    } else {
                        append(c)
                    }
                }
                else -> {
                    if (unicode && (c.code < 0x20 || c.code > 0x7f)) {
                        append("\\u${c.code.toString(16).padStart(4, '0')}")
                    } else {
                        append(c)
                    }
                }
            }
        }
    }
}