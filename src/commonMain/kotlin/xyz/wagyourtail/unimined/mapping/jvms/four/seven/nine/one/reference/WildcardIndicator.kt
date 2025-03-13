package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * WildcardIndicator:
 *   +
 *   -
 */
@JvmInline
value class WildcardIndicator private constructor(val value: Char) : Type {

    companion object: TypeCompanion<WildcardIndicator> {

        private val types = setOf('+', '-')

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() in types
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val value = reader.take()
            if (value !in types) {
                throw IllegalArgumentException("Invalid wildcard indicator")
            }
            append(value!!)
        }

        override fun unchecked(value: String) = WildcardIndicator(value.toCharArray()[0])
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value.toString()

}