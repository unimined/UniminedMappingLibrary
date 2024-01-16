package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeString
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
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
        override fun shouldRead(reader: BufferedSource): Boolean {
            val first = reader.readUtf8CodePoint().checkedToChar()
            // string or number
            if (first == '"' || first?.isDigit() == true) {
                return true
            }
            // boolean
            if (first == 't') {
                return reader.readUtf8CodePoint().checkedToChar() == 'r' &&
                        reader.readUtf8CodePoint().checkedToChar() == 'u' &&
                        reader.readUtf8CodePoint().checkedToChar() == 'e'
            }
            if (first == 'f') {
                return reader.readUtf8CodePoint().checkedToChar() == 'a' &&
                        reader.readUtf8CodePoint().checkedToChar() == 'l' &&
                        reader.readUtf8CodePoint().checkedToChar() == 's' &&
                        reader.readUtf8CodePoint().checkedToChar() == 'e'
            }
            return false
        }

        override fun read(reader: BufferedSource) = try {
            Constant(buildString {
                // string
                if (reader.peek().readUtf8CodePoint().checkedToChar() == '"') {
                    append(reader.takeString())
                    return@buildString
                }
                // boolean
                val value = reader.takeUTF8Until { it.checkedToChar() in annotationIdentifierIllegalCharacters }
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