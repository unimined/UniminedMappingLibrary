package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitOrExpression:
 *   [[BitOrExpression] "|"] [BitXorExpression]
 */
@JvmInline
value class BitOrExpression(val value: String) : Type {

    companion object : TypeCompanion<BitOrExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitXorExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            BitOrExpression(buildString {
                append(BitXorExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '|') {
                    append(" ")
                    append(reader.take())
                    append(" ")
                    reader.takeWhitespace()
                    append(BitXorExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid bit or expression", e)
        }

        override fun unchecked(value: String): BitOrExpression {
            return BitOrExpression(value)
        }

    }

    fun getParts(): Pair<BitOrExpression?, BitXorExpression> {
        val opIndex = value.lastIndexOf("|")
        if (opIndex == -1) {
            return null to BitXorExpression.unchecked(value)
        }
        return BitOrExpression.unchecked(value.substring(0, opIndex).trimEnd()) to BitXorExpression.unchecked(
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
                visitor(" | ")
            }
            right.accept(visitor)
        }
    }

    override fun toString() = value

}