package xyz.wagyourtail.unimined.mapping.annotation

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeString
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import xyz.wagyourtail.unimined.mapping.util.translateEscapes
import kotlin.jvm.JvmInline

/**
 * AnnotationElementName:
 *   AnnotationIdentifier
 *   " String "
 */

@JvmInline
value class AnnotationElementName private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElementName> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.peek().readUtf8CodePoint().checkedToChar() !in annotationIdentifierIllegalCharacters
        }

        override fun read(reader: BufferedSource) = try {
            AnnotationElementName(buildString {
                if (reader.peek().readUtf8CodePoint().checkedToChar() == '"') {
                    append(reader.takeString())
                    return@buildString
                }
                append(reader.takeUTF8Until { it.checkedToChar() in annotationIdentifierIllegalCharacters })
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