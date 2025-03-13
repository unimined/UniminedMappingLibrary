package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline
import kotlin.math.max

/**
 * MultiplicativeExpression:
 *   [[MultiplicativeExpression] ("*" | "/" | "%")] [UnaryExpression]
 */
@JvmInline
value class MultiplicativeExpression(val value: String) : Type {

    companion object : TypeCompanion<MultiplicativeExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return UnaryExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            MultiplicativeExpression(buildString {
                append(UnaryExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '*' || reader.peek() == '/' || reader.peek() == '%') {
                    append(" ")
                    append(reader.take())
                    append(" ")
                    reader.takeWhitespace()
                    append(UnaryExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid multiplicative expression", e)
        }

        override fun unchecked(value: String): MultiplicativeExpression {
            return MultiplicativeExpression(value)
        }

    }

    fun getParts(): Triple<MultiplicativeExpression?, String?, UnaryExpression> {
        val opIndex: Int = max(max(value.lastIndexOf("*"), value.lastIndexOf("/")), value.lastIndexOf("%"))
        if (opIndex == -1) {
            return Triple(null, null, UnaryExpression.unchecked(value))
        }
        return Triple(
            MultiplicativeExpression.unchecked(value.substring(0, opIndex).trimEnd()), value.substring(opIndex, opIndex + 1),
            UnaryExpression.unchecked(value.substring(opIndex + 1).trimStart())
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