package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * ExponentConstant:
 *   [-|+] [DecimalConstant]
 */
@JvmInline
value class ExponentConstant(val value: String) : Type {

    companion object : TypeCompanion<ExponentConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.peek()
            if (first == '-' || first == '+') {
                reader.take()
                return true
            }
            return DecimalConstant.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            ExponentConstant(buildString {
                val first = reader.peek()
                if (first == '-' || first == '+') {
                    append(reader.take())
                }
                append(DecimalConstant.read(reader))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid exponent constant", e)
        }

        override fun unchecked(value: String): ExponentConstant {
            return ExponentConstant(value)
        }

    }

    fun isNegative() = value.startsWith('-')

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value
}