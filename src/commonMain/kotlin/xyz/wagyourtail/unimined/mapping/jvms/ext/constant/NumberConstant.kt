package xyz.wagyourtail.unimined.mapping.jvms.ext.constant

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.number.*
import kotlin.jvm.JvmInline

/**
 * Number:
 *   [-] [[WholeConstant]] [. [DecimalConstant]] [e [ExponentConstant]] ["f" | "F" | "d" | "D"]
 *   [-] [WholeConstant] ["L" | "l"]
 *   [-] 0. [DecimalConstant] [e [ExponentConstant]] ["f" | "F" | "d" | "D"]
 *   [-] 0x [HexConstant] ["l" | "L"]
 *   [-] 0b [BinaryConstant] ["l" | "L"]
 *   [-] 0 ["f" | "F" | "d" | "D"]
 *   [-] 0 [[OctalConstant]] ["l" | "L"]
 *   [-] NaN ["f" | "F" | "d" | "D"]
 *   [-] Infinity ["f" | "F" | "d" | "D"]
 */
@JvmInline
value class NumberConstant private constructor(val value: String) : Type {

    companion object : TypeCompanion<NumberConstant> {

        val decimalSuffix = setOf('f', 'd')

        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.take()
            return first == '-' || first?.isDigit() == true || first == 'N' || first == 'I'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val first = reader.peek()
            if (first == '-') {
                append(reader.take()!!)
            }
            val next = reader.peek()
            if (next?.isDigit() == true && next != '0') {
                append(WholeConstant.read(reader))
                if (reader.peek()?.lowercaseChar() == 'l') {
                    append(reader.take()!!)
                    return
                }
                if (reader.peek() == '.') {
                    append(reader.take()!!)
                    append(DecimalConstant.read(reader))
                }
                if (reader.peek()?.lowercaseChar() == 'e') {
                    append(reader.take()!!)
                    append(ExponentConstant.read(reader))
                }
            } else if (next == '0') {
                append(reader.take()!!)
                val type = reader.peek()
                when (type?.lowercaseChar()) {
                    '.' -> {
                        append(reader.take()!!)
                        append(DecimalConstant.read(reader))
                        if (reader.peek()?.lowercaseChar() == 'e') {
                            append(reader.take()!!)
                            append(ExponentConstant.read(reader))
                        }
                    }
                    'x' -> {
                        append(reader.take()!!)
                        append(HexConstant.read(reader))
                        if (reader.peek()?.lowercaseChar() == 'l') {
                            append(reader.take()!!)
                        }
                        return
                    }
                    'b' -> {
                        append(reader.take()!!)
                        append(BinaryConstant.read(reader))
                        if (reader.peek()?.lowercaseChar() == 'l') {
                            append(reader.take()!!)
                        }
                        return
                    }
                    null -> {}
                    else -> {
                        if (type.isDigit()) {
                            append(OctalConstant.read(reader))
                            if (reader.peek()?.lowercaseChar() == 'l') {
                                append(reader.take()!!)
                            }
                            return
                        }
                    }
                }
            } else if (next == '.') {
                append(reader.take()!!)
                append(DecimalConstant.read(reader))
                if (reader.peek()?.lowercaseChar() == 'e') {
                    append(ExponentConstant.read(reader))
                }
            } else if (next == 'I') {
                for (char in "Infinity") {
                    reader.expect(char)
                }
                append("Infinity")
            } else if (next == 'N') {
                for (char in "NaN") {
                    reader.expect(char)
                }
                append("NaN")
            } else {
                throw IllegalArgumentException("Not a number")
            }
            if (reader.peek()?.lowercaseChar() in decimalSuffix) {
                append(reader.take()!!)
            }
        }

        override fun unchecked(value: String) = NumberConstant(value)

    }

    fun asPositive() = value.removePrefix("-")

    fun asNegative() = "-" + asPositive()

    fun isWhole() = isHex() || (value.last() !in decimalSuffix && !value.contains(".") && !value.lowercase().contains('e'))

    fun isDecimal() = value.last().lowercaseChar() in decimalSuffix || (value.last().lowercaseChar() != 'l') && (!asPositive().startsWith("0") || asPositive().startsWith("0."))

    fun isHex() = asPositive().substring(0, 2).lowercase().startsWith("0x")

    fun isBin() = asPositive().substring(0, 2).lowercase().startsWith("0b")

    fun isOctal() = asPositive().first() == '0' && asPositive().getOrNull(1)?.isDigit() == true

    fun isFloat() = value.last().lowercaseChar() == 'f' && !isHex()

    fun isLong() = value.last().lowercaseChar() == 'l'

    fun isDouble() = (asPositive().startsWith("I") || asPositive().startsWith("N") || isDecimal()) && !isFloat()

    fun isInteger() = isWhole() && !isLong()

    fun asNumber(): Number {
        return if (isFloat()) {
            value.toFloat()
        } else if (isLong()) {
            value.toLong()
        } else if (isDouble()) {
            value.toDouble()
        } else if (isInteger()) {
            value.toInt()
        } else {
            if (isHex()) {
                if (isLong()) {
                    value.substring(2).toLong(16)
                } else {
                    value.substring(2).toInt(16)
                }
            } else if (isBin()) {
                if (isLong()) {
                    value.substring(2).toLong(2)
                } else {
                    value.substring(2).toInt(2)
                }
            } else {
                if (isLong()) {
                    value.toLong(8)
                } else {
                    value.toInt(8)
                }
            }
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val reader = StringCharReader(value)
            read(reader) {
                if (it !is Type) {
                    visitor(it)
                } else {
                    it.accept(visitor)
                }
            }
            if (!reader.exhausted()) {
                throw IllegalStateException("Not fully read: \"${reader.takeRemaining().let { if (it.length > 100) it.substring(0, 100) + "..." else it }.escape()}\"")
            }
        }
    }

    override fun toString() = value

}