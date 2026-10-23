package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitXorExpression:
 *   [[BitXorExpression] "^"] [BitAndExpression]
 */
@JvmInline
value class BitXorExpression(val value: String) : Type {

    companion object : TypeCompanion<BitXorExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitAndExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(BitAndExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '^') {
                append(" ${reader.take()!!} ")
                reader.takeWhitespace()
                append(BitAndExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): BitXorExpression {
            return BitXorExpression(value)
        }

    }

    fun getParts(): Pair<List<BitAndExpression>, BitAndExpression> {
        val list = mutableListOf<BitAndExpression>()
        var last: BitAndExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is BitAndExpression -> last = it
                " ^ " -> list.add(last!!)
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