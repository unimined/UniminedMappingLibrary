package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * BitShiftExpression:
 *   [[BitShiftExpression] ("<<" | ">>" | ">>>")] [AdditiveExpression]
 */
@JvmInline
value class BitShiftExpression(val value: String) : Type {

    companion object : TypeCompanion<BitShiftExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AdditiveExpression.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(AdditiveExpression.read(reader))
            reader.takeWhitespace()
            while (reader.peek() == '<' || reader.peek() == '>') {
                val op = reader.peek()
                val operator = StringBuilder()
                if (op == '<') {
                    operator.append(reader.take()!!)
                    operator.append(reader.expect('<'))
                }
                if (op == '>') {
                    operator.append(reader.take()!!)
                    operator.append(reader.expect('>'))
                    val next = reader.peek()
                    if (next == '>') {
                        operator.append(reader.take()!!)
                    }
                }
                append(" $operator ")
                reader.takeWhitespace()
                append(AdditiveExpression.read(reader))
                reader.takeWhitespace()
            }
        }

        override fun unchecked(value: String): BitShiftExpression {
            return BitShiftExpression(value)
        }

    }

    fun getParts(): Pair<List<Pair<AdditiveExpression, String>>, AdditiveExpression> {
        val list = mutableListOf<Pair<AdditiveExpression, String>>()
        var last: AdditiveExpression? = null
        read(StringCharReader(value)) {
            when (it) {
                is AdditiveExpression -> last = it
                " << " -> list.add(Pair(last!!, "<<"))
                " >> " -> list.add(Pair(last!!, ">>"))
                " >>> " -> list.add(Pair(last!!, ">>>"))
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