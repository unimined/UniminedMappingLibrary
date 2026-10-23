package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * CastExpression:
 *   "<" [DataType] ">" [UnaryExpression]
 */
@JvmInline
value class CastExpression(val value: String) : Type {

    companion object : TypeCompanion<CastExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '<'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('<'))
            append(DataType.read(reader))
            append(reader.expect('>'))
            reader.takeWhitespace()
            append(UnaryExpression.read(reader))
        }

        override fun unchecked(value: String): CastExpression {
            return CastExpression(value)
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("<")
            DataType.unchecked(value.substringBefore(">").substring(1)).accept(visitor)
            visitor(">")
            UnaryExpression.unchecked(value.substringAfter(">").trimStart()).accept(visitor)
        }
    }

    override fun toString() = value

}