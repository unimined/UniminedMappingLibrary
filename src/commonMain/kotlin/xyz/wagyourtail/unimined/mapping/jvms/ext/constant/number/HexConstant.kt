package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * HexConstant:
 *   [0-9a-fA-F]+
 */
@JvmInline
value class HexConstant private constructor(val value: String): Type {

    companion object : TypeCompanion<HexConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.take()
            return first?.isDigit() == true || first?.lowercaseChar() in 'a'..'f'
        }

        override fun read(reader: CharReader<*>) = try {
            val first = reader.peek()
            if (first?.isDigit() != true) {
                throw IllegalArgumentException("Invalid hex constant, cannot start with ${first ?: "null"}")
            }
            HexConstant(reader.takeWhile { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid hex constant", e)
        }

        override fun unchecked(value: String): HexConstant {
            return HexConstant(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}