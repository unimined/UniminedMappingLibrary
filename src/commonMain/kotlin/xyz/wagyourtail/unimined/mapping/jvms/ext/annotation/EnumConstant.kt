package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.util.CharReader
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
        override fun shouldRead(reader: CharReader): Boolean {
            if (!ObjectType.shouldRead(reader.copy())) {
                return false
            }
            ObjectType.read(reader)
            if (reader.exhausted()) return false
            return reader.take() == '.'
        }

        override fun read(reader: CharReader) = try {
            EnumConstant(buildString {
                append(ObjectType.read(reader))
                val next = reader.take()
                if (next != '.') {
                    throw IllegalArgumentException("Invalid enum constant, expected ., found $next")
                }
                append('.')
                if (reader.peek() == '"') {
                    val str = reader.takeString()
                    if (str.length <= 2) {
                        throw IllegalArgumentException("Invalid enum constant, found $str which is empty")
                    }
                    append(str)
                } else {
                    val value = reader.takeUntil { it in annotationIdentifierIllegalCharacters }
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

    fun getParts(): Pair<ObjectType, String> = CharReader(value).use {
        val obj = ObjectType.read(it)
        val next = it.take()
        if (next != '.') {
            throw IllegalArgumentException("Invalid enum constant, expected ., found $next")
        }
        val str = if (it.peek() == '"') {
            it.takeString()
        } else {
            val value = it.takeUntil { it in annotationIdentifierIllegalCharacters }
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