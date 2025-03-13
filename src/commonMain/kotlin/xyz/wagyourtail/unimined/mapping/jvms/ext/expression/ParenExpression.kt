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

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('('))
            reader.takeWhitespace()
            append(Expression.read(reader))
            reader.takeWhitespace()
            append(reader.expect(')'))
        }

        override fun unchecked(value: String): ParenExpression {
            return ParenExpression(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("(")
            Expression.unchecked(value.substring(1, value.length - 1).trim()).accept(visitor)
            visitor(")")
        }
    }

    override fun toString() = value

}