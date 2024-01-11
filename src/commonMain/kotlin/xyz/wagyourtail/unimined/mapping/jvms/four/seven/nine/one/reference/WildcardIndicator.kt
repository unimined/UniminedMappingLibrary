package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * WildcardIndicator:
 *   +
 *   -
 */
@JvmInline
value class WildcardIndicator private constructor(val value: Char) {

    companion object: TypeCompanion<WildcardIndicator> {

        private val types = setOf('+', '-')

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() in types
        }

        override fun read(reader: BufferedSource): WildcardIndicator {
            val value = reader.readUtf8CodePoint()
            if (value.checkedToChar() !in types) {
                throw IllegalArgumentException("Invalid wildcard indicator")
            }
            return WildcardIndicator(value.toChar())
        }
    }

    override fun toString() = value.toString()

}