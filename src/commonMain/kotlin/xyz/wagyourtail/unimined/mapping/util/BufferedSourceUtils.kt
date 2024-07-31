package xyz.wagyourtail.unimined.mapping.util

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
