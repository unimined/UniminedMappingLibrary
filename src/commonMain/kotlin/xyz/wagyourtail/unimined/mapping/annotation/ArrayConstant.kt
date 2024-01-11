package xyz.wagyourtail.unimined.mapping.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayConstant:
 *   { [ArrayElements] }
 */
@JvmInline
value class ArrayConstant private constructor(val value: String) {

    companion object: TypeCompanion<ArrayConstant> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '{'
        }

        override fun read(reader: BufferedSource) = try {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid array constant")
            }
            ArrayConstant(buildString {
                append('{')
                if (reader.peek().readUtf8CodePoint().checkedToChar() != '}') {
                    append(ArrayElements.read(reader))
                }
                val end = reader.readUtf8CodePoint().checkedToChar()
                if (end != '}') {
                    throw IllegalArgumentException("Invalid array constant, expected }, found $end")
                }
                append('}')
            })
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid array constant", e)
        }
    }

    fun getParts(): ArrayElements? = Buffer().use {
        it.writeUtf8(value.substring(1))
        if (it.peek().readUtf8CodePoint().checkedToChar() == '}') {
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