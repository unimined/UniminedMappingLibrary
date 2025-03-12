package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationIdentifier.Companion.annotationIdentifierIllegalCharacters
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Identifier
import kotlin.jvm.JvmInline

/**
 * EnumConstant:
 *   [ObjectType] . [AnnotationIdentifier]
 *
 */
@JvmInline
value class EnumConstant private constructor(val value: String) : Type {

    companion object: TypeCompanion<EnumConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (!ObjectType.shouldRead(reader.copy())) {
                return false
            }
            ObjectType.read(reader)
            if (reader.exhausted()) return false
            return reader.take() == '.'
        }

        override fun read(reader: CharReader<*>) = try {
            EnumConstant(buildString {
                append(ObjectType.read(reader))
                val next = reader.take()
                if (next != '.') {
                    throw IllegalArgumentException("Invalid enum constant, expected ., found $next")
                }
                append('.')
                append(AnnotationIdentifier.read(reader))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid enum constant", e)
        }

        override fun unchecked(value: String) = EnumConstant(value)
    }

    fun getParts(): Pair<ObjectType, String> = StringCharReader(value).let {
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (obj, str) = getParts()
            obj.accept(visitor)
            visitor(".")
            visitor(str)
        }
    }

    override fun toString() = value

}