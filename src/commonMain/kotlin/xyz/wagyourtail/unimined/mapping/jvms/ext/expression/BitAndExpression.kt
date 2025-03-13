package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitAndExpression:
 *   [[BitAndExpression] "&"] [BitShiftExpression]
 */
@JvmInline
value class BitAndExpression(val value: String) : Type {

    companion object : TypeCompanion<BitAndExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitShiftExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            BitAndExpression(buildString {
                append(BitShiftExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '&') {
                    append(" ")
                    append(reader.take())
                    append(" ")
                    reader.takeWhitespace()
                    append(BitShiftExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid bit and expression", e)
        }

        override fun unchecked(value: String): BitAndExpression {
            return BitAndExpression(value)
        }

    }

    fun getParts(): Pair<BitAndExpression?, BitShiftExpression> {
        val opIndex = value.lastIndexOf("&")
        if (opIndex == -1) {
            return null to BitShiftExpression.unchecked(value)
        }
        return BitAndExpression.unchecked(value.substring(0, opIndex).trimEnd()) to BitShiftExpression.unchecked(
            value.substring(
                opIndex + 1
            ).trimStart()
        )
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (left, right) = getParts()
            if (left != null) {
                left.accept(visitor)
                visitor(" & ")
            }
            right.accept(visitor)
        }
    }

    override fun toString() = value

}