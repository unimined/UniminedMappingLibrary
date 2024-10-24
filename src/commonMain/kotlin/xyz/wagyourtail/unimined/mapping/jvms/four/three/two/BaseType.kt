package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * BaseType:
 *   (one of)
 *   B C D F I J S Z
 */
@JvmInline
value class BaseType private constructor(val value: Char) {

    companion object: TypeCompanion<BaseType> {
        private val types = setOf('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z')

        override fun shouldRead(reader: CharReader<*>): Boolean {
            val value = reader.take()
            return value in types
        }

        override fun read(reader: CharReader<*>): BaseType {
            val value = reader.take()
            if (value !in types) {
                throw IllegalArgumentException("Invalid base type")
            }
            return BaseType(value!!)
        }

        override fun unchecked(value: String) = BaseType(value.toCharArray()[0])
    }

    fun getWidth() = when (value) {
        'J', 'D' -> 2
        else -> 1
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value.toString()

}