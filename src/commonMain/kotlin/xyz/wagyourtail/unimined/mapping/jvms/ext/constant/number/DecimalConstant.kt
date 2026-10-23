package xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * DecimalConstant:
 * \d+
 */
@JvmInline
value class DecimalConstant private constructor(val value: String) : Type {

    companion object : TypeCompanion<DecimalConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take()?.isDigit() == true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val first = reader.peek()
            if (first?.isDigit() != true) {
                throw IllegalArgumentException("Invalid decimal constant, expected number, got $first")
            }
            append(reader.takeWhile { it.isDigit() })
        }

        override fun unchecked(value: String): DecimalConstant {
            return DecimalConstant(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value
}