package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitAndExpression:
 *   [[BitAndExpression] "&"] [BitShiftExpression]
 */
@JvmInline
value class BitAndExpression(val value: String) : Type {

    companion object : TypeCompanion<BitAndExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return BitShiftExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(BitShiftExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '&') {
                append(" ${reader.take()!!} ")
                reader.takeWhitespace()
                append(BitShiftExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): BitAndExpression {
            return BitAndExpression(value)
        }

    }

    fun getParts(): Pair<List<BitShiftExpression>, BitShiftExpression> {
        val list = mutableListOf<BitShiftExpression>()
        var last: BitShiftExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is BitShiftExpression -> last = it
                " & " -> list.add(last!!)
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