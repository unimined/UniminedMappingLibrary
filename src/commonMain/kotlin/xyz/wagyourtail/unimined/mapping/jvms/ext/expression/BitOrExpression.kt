package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitOrExpression:
 *   [[BitOrExpression] "|"] [BitXorExpression]
 */
@JvmInline
value class BitOrExpression(val value: String) : Type {

    companion object : TypeCompanion<BitOrExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitXorExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(BitXorExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '|') {
                append(" ${reader.take()!!} ")
                reader.takeWhitespace()
                append(BitXorExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): BitOrExpression {
            return BitOrExpression(value)
        }

    }

    fun getParts(): Pair<List<BitXorExpression>, BitXorExpression> {
        val list = mutableListOf<BitXorExpression>()
        var last: BitXorExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is BitXorExpression -> last = it
                " | " -> list.add(last!!)
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