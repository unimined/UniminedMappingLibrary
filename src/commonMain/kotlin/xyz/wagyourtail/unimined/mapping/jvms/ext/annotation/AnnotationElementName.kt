package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationIdentifier.Companion.annotationIdentifierIllegalCharacters
import kotlin.jvm.JvmInline

/**
 * AnnotationElementName:
 *   AnnotationIdentifier
 */

@JvmInline
value class AnnotationElementName private constructor(val value: AnnotationIdentifier) : Type {

    companion object: TypeCompanion<AnnotationElementName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AnnotationIdentifier.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            AnnotationElementName(AnnotationIdentifier.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element name", e)
        }

        override fun unchecked(value: String) = AnnotationElementName(AnnotationIdentifier.unchecked(value))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}