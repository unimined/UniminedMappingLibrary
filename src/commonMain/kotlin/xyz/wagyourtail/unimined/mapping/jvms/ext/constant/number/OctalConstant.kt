package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * OctalConstant:
 *   [0-7]+
 */
@JvmInline
value class OctalConstant private constructor(val value: String): Type {

    companion object : TypeCompanion<OctalConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.take()
            return first?.isDigit() == true && first != '8' && first != '9'
        }

        override fun read(reader: CharReader<*>) = try {
            val first = reader.peek()
            if (first?.isDigit() != true) {
                throw IllegalArgumentException("Invalid octal constant, cannot start with ${first ?: "null"}")
            }
            OctalConstant(reader.takeWhile { it.isDigit() && it != '8' && it != '9' })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid octal constant", e)
        }

        override fun unchecked(value: String): OctalConstant {
            return OctalConstant(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}