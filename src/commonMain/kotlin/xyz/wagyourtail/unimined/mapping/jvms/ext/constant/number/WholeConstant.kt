package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * WholeConstant:
 *   [1-9][0-9]*
 */
@JvmInline
value class WholeConstant private constructor(val value: String): Type {

    companion object : TypeCompanion<WholeConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.take()
            return first?.isDigit() == true && first != '0'
        }

        override fun read(reader: CharReader<*>) = try {
            val first = reader.peek()
            if (first == '0') {
                throw IllegalArgumentException("Invalid whole constant, cannot start with 0")
            }
            if (first?.isDigit() != true) {
                throw IllegalArgumentException("Invalid whole constant, cannot start with ${first ?: "null"}")
            }
            WholeConstant(reader.takeWhile { it.isDigit() })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid whole constant", e)
        }

        override fun unchecked(value: String): WholeConstant {
            return WholeConstant(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}