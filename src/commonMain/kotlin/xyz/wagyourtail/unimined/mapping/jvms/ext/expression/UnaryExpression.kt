package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * UnaryExpression:
 *   "-" [PrimaryExpression]
 *   "~" [PrimaryExpression]
 *   [PrimaryExpression]
 *   "-" [CastExpression]
 *   "~" [CastExpression]
 *   [CastExpression]
 *
 */
@JvmInline
value class UnaryExpression(val value: String) : Type {

    companion object : TypeCompanion<UnaryExpression> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(CastExpression, PrimaryExpression)

        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.peek()
            if (first == '-' || first == '~') {
                reader.take()
                return true
            }
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            if (reader.peek() == '-') {
                append(reader.take()!!)
            } else if (reader.peek() == '~') {
                append(reader.take()!!)
            }
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String): UnaryExpression {
            return UnaryExpression(value)
        }

    }

    fun getParts(): Pair<String?, PrimaryExpression> {
        if (value.startsWith("-") || value.startsWith("~")) {
            return Pair(value.substring(0, 1), PrimaryExpression.unchecked(value.substring(1)))
        }
        return Pair(null, PrimaryExpression.unchecked(value))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (prefix, expression) = getParts()
            if (prefix != null) visitor(prefix)
            expression.accept(visitor)
        }
    }

    override fun toString() = value
}