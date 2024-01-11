package xyz.wagyourtail.unimined.mapping.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeString
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * EnumConstant:
 *   [ObjectType] . Identifier
 *   [ObjectType] . " String "
 *
 */
@JvmInline
value class EnumConstant private constructor(val value: String) {

    companion object: TypeCompanion<EnumConstant> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            if (!ObjectType.shouldRead(reader.peek())) {
                return false
            }
            ObjectType.read(reader)
            if (reader.exhausted()) return false
            return reader.readUtf8CodePoint().checkedToChar() == '.'
        }

        override fun read(reader: BufferedSource) = try {
            EnumConstant(buildString {
                append(ObjectType.read(reader))
                val next = reader.readUtf8CodePoint().checkedToChar()
                if (next != '.') {
                    throw IllegalArgumentException("Invalid enum constant, expected ., found $next")
                }
                append('.')
                if (reader.peek().readUtf8CodePoint().checkedToChar() == '"') {
                    val str = reader.takeString()
                    if (str.length <= 2) {
                        throw IllegalArgumentException("Invalid enum constant, found $str which is empty")
                    }
                    append(str)
                } else {
                    val value = reader.takeUTF8Until { it.checkedToChar() in annotationIdentifierIllegalCharacters }
                    if (value.isEmpty()) {
                        throw IllegalArgumentException("Invalid enum constant, expected identifier, found $value")
                    }
                    append(value)
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid enum constant", e)
        }
    }

    fun getParts(): Pair<ObjectType, String> = Buffer().use {
        it.writeUtf8(value)
        val obj = ObjectType.read(it)
        val next = it.readUtf8CodePoint().checkedToChar()
        if (next != '.') {
            throw IllegalArgumentException("Invalid enum constant, expected ., found $next")
        }
        val str = if (it.peek().readUtf8CodePoint().checkedToChar() == '"') {
            it.takeString()
        } else {
            val value = it.takeUTF8Until { it.checkedToChar() in annotationIdentifierIllegalCharacters }
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid enum constant, expected identifier, found $value")
            }
            value
        }
        Pair(obj, str)
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val (obj, str) = getParts()
            obj.accept(visitor)
            visitor(".", true)
            visitor(str, true)
        }
    }

    override fun toString() = value

}