package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * ArrayConstant:
 *   { [ArrayElements] }
 */
@JvmInline
value class ArrayConstant private constructor(val value: String) {

    companion object: TypeCompanion<ArrayConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '{'
        }

        override fun read(reader: CharReader<*>) = try {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid array constant")
            }
            ArrayConstant(buildString {
                append('{')
                if (reader.peek() != '}') {
                    append(ArrayElements.read(reader))
                }
                val end = reader.take()
                if (end != '}') {
                    throw IllegalArgumentException("Invalid array constant, expected }, found $end")
                }
                append('}')
            })
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid array constant", e)
        }

        override fun unchecked(value: String) = ArrayConstant(value)
    }

    fun getParts(): ArrayElements? {
        if (value.length <= 2) {
            return null
        }
        return ArrayElements.unchecked(value.substring(1, value.length - 1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("{", true)
            getParts()?.accept(visitor)
            visitor("}", true)
        }
    }

    override fun toString() = value

}