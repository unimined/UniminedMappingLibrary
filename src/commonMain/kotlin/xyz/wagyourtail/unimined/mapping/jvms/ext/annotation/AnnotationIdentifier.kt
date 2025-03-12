package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * AnnotationIdentifier:
 *   " String "
 *   AnnotationIdentifier
 */
@JvmInline
value class AnnotationIdentifier private constructor(val value: String) : Type {
    companion object: TypeCompanion<AnnotationIdentifier> {

        val annotationIdentifierIllegalCharacters = JVMS.unqualifiedNameIllegalChars + setOf('=', ',', ')', '}')

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in annotationIdentifierIllegalCharacters
        }

        override fun read(reader: CharReader<*>) = try {
            val value = buildString {
                if (reader.peek() == '"') {
                    append("\"")
                    append(reader.takeString().escape(true))
                    append("\"")
                    return@buildString
                }
                append(reader.takeUntil { it in annotationIdentifierIllegalCharacters })
            }
            AnnotationIdentifier(value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid identifier name", e)
        }

        override fun unchecked(value: String) = AnnotationIdentifier(value)

    }

    fun unescape(): String {
        if (value.first() == '"') {
            return value.substring(1, value.length - 1).translateEscapes()
        }
        return value
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}