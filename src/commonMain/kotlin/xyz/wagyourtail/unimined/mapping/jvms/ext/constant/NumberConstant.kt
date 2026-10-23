package xyz.wagyourtail.unimined.mapping.jvms.ext.constant

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.unaryMinus
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
            return first == '-' || first?.isDigit() == true || first == 'N' || first == 'I' || first == '.'
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
                    append(reader.take()!!)
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

    fun isNegative() = value.startsWith("-")

    fun asPositive() = value.removePrefix("-")

    fun asNegative() = "-" + asPositive()

    fun isWhole(): Boolean {
        val positive = asPositive()
        // must be whole due to length
        if (positive.length == 1) {
            return true
        }
        // check for leading 0
        if (positive.first() == '0') {
            return false
        }
        // check for decimal suffix
        if (value.last().lowercaseChar() in decimalSuffix) {
            return false
        }
        // check for decimal/exponent
        if (value.contains('.', true) || value.contains('e', true)) {
            return false
        }
        return true
    }

    fun isDecimal(): Boolean {
        // has decimal suffix
        if (value.last().lowercaseChar() in decimalSuffix) {
            return true
        }
        // is not a long
        if (value.last().lowercaseChar() == 'l') {
            return false
        }
        // explicit checks
        return value.contains('.', true) || value.contains('e', true)
    }

    fun isHex() = asPositive().lowercase().startsWith("0x")

    fun isBin() = asPositive().lowercase().startsWith("0b")

    fun isOctal(): Boolean {
        val positive = asPositive()
        // too short
        if (positive.length == 1) {
            return false
        }
        if (positive.first() == '0' && positive[1].isDigit()) {
            return true
        }
        return false
    }

    fun isFloat() = value.last().lowercaseChar() == 'f' && !isHex()

    fun isLong() = value.last().lowercaseChar() == 'l'

    fun isDouble(): Boolean {
        val positive = asPositive()
        // infinity or nan
        if (positive.startsWith("I") || positive.startsWith("N")) {
            return true
        }
        if (isDecimal()) {
             return value.last().lowercaseChar() != 'f'
        }
        return false
    }

    fun isInteger() = isWhole() && !isLong()

    fun asNumber(): Number {
        if (isNegative()) {
            return -unchecked(asPositive()).asNumber()
        }
        return if (isHex()) {
            if (isLong()) {
                value.substring(2, value.length - 1).toLong(16)
            } else {
                value.substring(2).toInt(16)
            }
        } else if (isBin()) {
            if (isLong()) {
                value.substring(2, value.length - 1).toLong(2)
            } else {
                value.substring(2).toInt(2)
            }
        } else if (isOctal()) {
            if (isLong()) {
                value.substring(0, value.length - 1).toLong(8)
            } else {
                value.toInt(8)
            }
        } else if (isFloat()) {
            value.substring(0, value.length - 1).toFloat()
        } else if (isLong()) {
            value.substring(0, value.length - 1).toLong()
        } else if (isDouble()) {
            if (value.last().lowercaseChar() == 'd') {
                value.substring(0, value.length - 1).toDouble()
            } else {
                value.toDouble()
            }
        } else if (isInteger()) {
            value.toInt()
        } else if (isLong()) {
            value.substring(0, value.length - 1).toLong()
        } else {
            throw IllegalStateException()
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