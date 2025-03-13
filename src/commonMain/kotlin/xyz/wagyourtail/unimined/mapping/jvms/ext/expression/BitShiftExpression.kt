package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitShiftExpression:
 *   [[BitShiftExpression] ("<<" | ">>" | ">>>")] [AdditiveExpression]
 */
@JvmInline
value class BitShiftExpression(val value: String) : Type {

    companion object : TypeCompanion<BitShiftExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AdditiveExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            BitShiftExpression(buildString {
                append(AdditiveExpression.read(reader))
                reader.takeWhitespace()
                while (reader.peek() == '<' || reader.peek() == '>') {
                    val op = reader.peek()
                    append(" ")
                    if (op == '<') {
                        append(reader.take())
                        append(reader.expect('<'))
                    }
                    if (op == '>') {
                        append(reader.take())
                        append(reader.expect('>'))
                        val next = reader.peek()
                        if (next == '>') {
                            append(reader.take())
                        }
                    }
                    append(" ")
                    reader.takeWhitespace()
                    append(AdditiveExpression.read(reader))
                    reader.takeWhitespace()
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid bit shift expression", e)
        }

        override fun unchecked(value: String): BitShiftExpression {
            return BitShiftExpression(value)
        }

    }

    fun getParts(): Triple<BitShiftExpression?, String?, AdditiveExpression> {
        val (opIndex, op) = value.findLastAnyOf(setOf(">>>", ">>", "<<")) ?: return Triple(null, null,
            AdditiveExpression.unchecked(value)
        )
        return Triple(
            BitShiftExpression.unchecked(value.substring(0, opIndex).trimEnd()), op,
            AdditiveExpression.unchecked(value.substring(opIndex + op.length).trimStart())
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