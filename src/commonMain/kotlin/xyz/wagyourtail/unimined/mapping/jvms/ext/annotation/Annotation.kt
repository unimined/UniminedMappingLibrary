package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

val annotationIdentifierIllegalCharacters = JVMS.unqualifiedNameIllagalChars + setOf('=', ',', ')', '}')


/**
 * Annotation:
 *   @ [ObjectType] ( [[AnnotationElements]] ) [[Invisible]]
 */
@JvmInline
value class Annotation private constructor(val value: String) {

    companion object: TypeCompanion<Annotation> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '@'
        }

        override fun read(reader: BufferedSource) = try {
            Annotation(buildString {
                val at = reader.readUtf8CodePoint().checkedToChar()
                if (at != '@') {
                    throw IllegalArgumentException("Invalid annotation, expected @, found $at")
                }
                append('@')
                append(ObjectType.read(reader))
                val next = reader.readUtf8CodePoint().checkedToChar()
                if (next != '(') {
                    throw IllegalArgumentException("Invalid annotation, expected (, found $next")
                }
                append('(')
                if (AnnotationElements.shouldRead(reader.peek())) {
                    append(AnnotationElements.read(reader))
                }
                val end = reader.readUtf8CodePoint().checkedToChar()
                if (end != ')') {
                    throw IllegalArgumentException("Invalid annotation, expected ), found $end")
                }
                append(')')
                if (Invisible.shouldRead(reader.peek())) {
                    append(Invisible.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation", e)
        }

    }

    fun getParts(): Triple<ObjectType, AnnotationElements?, Invisible?> = Buffer().use {
        it.writeUtf8(value.substring(1))
        val obj = ObjectType.read(it)
        val open = it.readUtf8CodePoint().checkedToChar()
        if (open != '(') {
            throw IllegalArgumentException("Invalid annotation, expected (, found $open")
        }
        val elements = if (AnnotationElements.shouldRead(it.peek())) {
            AnnotationElements.read(it)
        } else {
            null
        }
        val close = it.readUtf8CodePoint().checkedToChar()
        if (close != ')') {
            throw IllegalArgumentException("Invalid annotation, expected ), found $close")
        }
        val invis = if (Invisible.shouldRead(it.peek())) {
            Invisible.read(it)
        } else {
            null
        }
        Triple(obj, elements, invis)
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        val (obj, elements, invis) = getParts()
        if (visitor(this, false)) {
            visitor("@", true)
            obj.accept(visitor)
            visitor("(", true)
            elements?.accept(visitor)
            visitor(")", true)
            invis?.accept(visitor)
        }
    }

    override fun toString() = value

}