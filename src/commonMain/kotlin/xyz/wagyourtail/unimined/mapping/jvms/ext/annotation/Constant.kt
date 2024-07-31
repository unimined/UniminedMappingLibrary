package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.translateEscapes
import kotlin.jvm.JvmInline

/**
 * Constant:
 *   " String "
 *   boolean
 *   integer
 *   float
 *   long
 *   double
 */
@JvmInline
value class Constant private constructor(val value: String) {

    companion object: TypeCompanion<Constant> {
        override fun shouldRead(reader: CharReader): Boolean {
            val first = reader.take()
            // string or number
            if (first == '"' || first?.isDigit() == true) {
                return true
            }
            // boolean
            if (first == 't') {
                return reader.take() == 'r' &&
                        reader.take() == 'u' &&
                        reader.take() == 'e'
            }
            if (first == 'f') {
                return reader.take() == 'a' &&
                        reader.take() == 'l' &&
                        reader.take() == 's' &&
                        reader.take() == 'e'
            }
            return false
        }

        override fun read(reader: CharReader) = try {
            Constant(buildString {
                // string
                if (reader.peek() == '"') {
                    append(reader.takeString())
                    return@buildString
                }
                // boolean
                val value = reader.takeUntil { it in annotationIdentifierIllegalCharacters }
                if (value == "true" || value == "false") {
                    append(value)
                    return@buildString
                }
                // float or double
                if (value.matches(Regex("[+-]?\\d*\\.?\\d+[fd]", RegexOption.IGNORE_CASE))) {
                    append(value)
                    return@buildString
                }
                // long or int
                if (value.matches(Regex("[+-]?\\d+l?", RegexOption.IGNORE_CASE))) {
                    append(value)
                    return@buildString
                }
                throw IllegalArgumentException("expected string, boolean or digit, found $value")
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid constant", e)
        }

        override fun unchecked(value: String) = Constant(value)
    }

    fun isString() = value.first() == '"'

    fun isBoolean() = value == "true" || value == "false"

    fun isFloat() = value.last() == 'f'

    fun isDouble() = value.last() == 'd'

    fun isLong() = value.last() == 'l'

    fun isInt() = value.all { it.isDigit() }

    fun getString() = value.substring(1, value.length - 1).translateEscapes()

    fun getBoolean() = value == "true"

    fun getFloat() = value.substring(0, value.length - 1).toFloat()

    fun getDouble() = value.substring(0, value.length - 1).toDouble()

    fun getLong() = value.substring(0, value.length - 1).toLong()

    fun getInt() = value.toInt()

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value

}