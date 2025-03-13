package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
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

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(UnaryExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '*' || reader.peek() == '/' || reader.peek() == '%') {
                append(" ${reader.take()!!} ")
                reader.takeWhitespace()
                append(UnaryExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): MultiplicativeExpression {
            return MultiplicativeExpression(value)
        }

    }

    fun getParts(): Pair<List<Pair<UnaryExpression, String>>, UnaryExpression> {
        val list = mutableListOf<Pair<UnaryExpression, String>>()
        var last: UnaryExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is UnaryExpression -> last = it
                " * " -> list.add(Pair(last!!, "*"))
                " / " -> list.add(Pair(last!!, "/"))
                " % " -> list.add(Pair(last!!, "%"))
                else -> throw IllegalStateException()
            }
        }
        return Pair(list, last!!)
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            read(StringCharReader(value)) {
                if (it !is Type) {
                    visitor(it)
                } else {
                    it.accept(visitor)
                }
            }
        }
    }

    override fun toString() = value
}