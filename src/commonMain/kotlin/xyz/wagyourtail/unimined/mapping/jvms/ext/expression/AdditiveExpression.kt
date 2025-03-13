package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
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

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(MultiplicativeExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '+' || reader.peek() == '-') {
                append(" ${reader.take()!!} ")
                reader.takeWhitespace()
                append(MultiplicativeExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): AdditiveExpression {
            return AdditiveExpression(value)
        }

    }

    fun getParts(): Pair<List<Pair<MultiplicativeExpression, String>>, MultiplicativeExpression> {
        val list = mutableListOf<Pair<MultiplicativeExpression, String>>()
        var last: MultiplicativeExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is MultiplicativeExpression -> last = it
                " + " -> list.add(Pair(last!!, "+"))
                " - " -> list.add(Pair(last!!, "-"))
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