package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

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

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value.toString()

}