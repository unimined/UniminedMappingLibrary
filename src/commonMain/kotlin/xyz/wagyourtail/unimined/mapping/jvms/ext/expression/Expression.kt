package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * Expression:
 *   [BitOrExpression]
 */
@JvmInline
value class Expression(val value: BitOrExpression) : Type {

    companion object : TypeCompanion<Expression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitOrExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(BitOrExpression.read(reader))
        }

        override fun unchecked(value: String): Expression {
            return Expression(BitOrExpression.unchecked(value))
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}

