package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitXorExpression:
 *   [[BitXorExpression] "^"] [BitAndExpression]
 */
@JvmInline
value class BitXorExpression(val value: String) : Type {

    companion object : TypeCompanion<BitXorExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitAndExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            BitXorExpression(buildString {
                append(BitAndExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '^') {
                    append(" ")
                    append(reader.take())
                    append(" ")
                    reader.takeWhitespace()
                    append(BitAndExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid bit xor expression", e)
        }

        override fun unchecked(value: String): BitXorExpression {
            return BitXorExpression(value)
        }

    }

    fun getParts(): Pair<BitXorExpression?, BitAndExpression> {
        val opIndex = value.lastIndexOf("^")
        if (opIndex == -1) {
            return null to BitAndExpression.unchecked(value)
        }
        return BitXorExpression.unchecked(value.substring(0, opIndex).trimEnd()) to BitAndExpression.unchecked(
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
                visitor(" ^ ")
            }
            right.accept(visitor)
        }
    }

    override fun toString() = value

}