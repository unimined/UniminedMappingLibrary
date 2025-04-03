package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BinaryConstant:
 *   [01]+
 */
@JvmInline
value class BinaryConstant private constructor(val value: String): Type {

    companion object : TypeCompanion<BinaryConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.take()
            return first in '0'..'1'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val first = reader.peek()
            if (first !in '0' .. '1') {
                throw IllegalArgumentException("Invalid binary constant, cannot start with ${first ?: "null"}")
            }
            append(reader.takeWhile { it in '0' .. '1' })
        }

        override fun unchecked(value: String): BinaryConstant {
            return BinaryConstant(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}