package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
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

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() in types
        }

        override fun read(reader: CharReader): WildcardIndicator {
            val value = reader.take()
            if (value !in types) {
                throw IllegalArgumentException("Invalid wildcard indicator")
            }
            return WildcardIndicator(value!!)
        }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value.toString()

}