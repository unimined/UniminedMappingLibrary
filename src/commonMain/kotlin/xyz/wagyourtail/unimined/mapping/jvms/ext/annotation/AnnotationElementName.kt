package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.*
import kotlin.jvm.JvmInline

/**
 * AnnotationElementName:
 *   AnnotationIdentifier
 *   " String "
 */

@JvmInline
value class AnnotationElementName private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElementName> {
        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() !in annotationIdentifierIllegalCharacters
        }

        override fun read(reader: CharReader) = try {
            AnnotationElementName(buildString {
                if (reader.peek() == '"') {
                    append(reader.takeString())
                    return@buildString
                }
                append(reader.takeUntil { it in annotationIdentifierIllegalCharacters })
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element name", e)
        }
    }

    fun unescape(): String {
        if (value.first() == '"') {
            return value.substring(1, value.length - 1).translateEscapes()
        }
        return value
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value

}