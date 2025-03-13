package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline
import kotlin.math.max

/**
 * AdditiveExpression:
 *   [[AdditiveExpression] ("+" | "-")] [MultiplicativeExpression]
 */
@JvmInline
value class AdditiveExpression(val value: String) : Type {

    companion object : TypeCompanion<AdditiveExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return MultiplicativeExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            AdditiveExpression(buildString {
                append(MultiplicativeExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '+' || reader.peek() == '-') {
                    append(" ")
                    append(reader.take())
                    append(" ")
                    reader.takeWhitespace()
                    append(MultiplicativeExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid additive expression", e)
        }

        override fun unchecked(value: String): AdditiveExpression {
            return AdditiveExpression(value)
        }

    }

    fun getParts(end: Int = value.length): Triple<AdditiveExpression?, String?, MultiplicativeExpression> {
        val v = value.substring(0, end)
        val opIndex: Int = max(v.lastIndexOf("+"), v.lastIndexOf("-"))
        if (opIndex == -1) {
            return Triple(null, null, MultiplicativeExpression.unchecked(value))
        }
        if (value[opIndex - 1].lowercaseChar() == 'e') {
            return getParts(opIndex)
        }
        return Triple(
            AdditiveExpression.unchecked(value.substring(0, opIndex).trimEnd()),
            value.substring(opIndex, opIndex + 1),
            MultiplicativeExpression.unchecked(value.substring(opIndex + 1).trimStart())
        )
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (left, op, right) = getParts()
            left?.accept(visitor)
            if (op != null) visitor(" $op ")
            right.accept(visitor)
        }
    }

    override fun toString() = value

}