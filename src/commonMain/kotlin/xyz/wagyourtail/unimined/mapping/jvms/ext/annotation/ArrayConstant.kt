package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayConstant:
 *   { [ArrayElements] }
 */
@JvmInline
value class ArrayConstant private constructor(val value: String) {

    companion object: TypeCompanion<ArrayConstant> {
        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '{'
        }

        override fun read(reader: CharReader) = try {
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
    }

    fun getParts(): ArrayElements? = CharReader(value.substring(1)).use {
        if (it.peek() == '}') {
            return null
        }
        return ArrayElements.read(it)
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