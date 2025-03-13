package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * ParenExpression:
 *  "(" [Expression] ")"
 */
@JvmInline
value class ParenExpression(val value: String) : Type {

    companion object : TypeCompanion<ParenExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '('
        }

        override fun read(reader: CharReader<*>) = try {
            ParenExpression(buildString {
                append(reader.expect('('))
                append(Expression.read(reader))
                append(reader.expect(')'))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid paren expression", e)
        }

        override fun unchecked(value: String): ParenExpression {
            return ParenExpression(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("(")
            Expression.unchecked(value.substring(1, value.length - 1)).accept(visitor)
            visitor(")")
        }
    }

    override fun toString() = value

}