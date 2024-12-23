package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

val annotationIdentifierIllegalCharacters = JVMS.unqualifiedNameIllegalChars + setOf('=', ',', ')', '}')


/**
 * Annotation:
 *   @ [ObjectType] ( [[AnnotationElements]] ) [[Invisible]]
 */
@JvmInline
value class Annotation private constructor(val value: String) {

    companion object: TypeCompanion<Annotation> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '@'
        }

        override fun read(reader: CharReader<*>) = try {
            Annotation(buildString {
                val at = reader.take()
                if (at != '@') {
                    throw IllegalArgumentException("Invalid annotation, expected @, found $at")
                }
                append('@')
                append(ObjectType.read(reader))
                val next = reader.take()
                if (next != '(') {
                    throw IllegalArgumentException("Invalid annotation, expected (, found $next")
                }
                append('(')
                if (AnnotationElements.shouldRead(reader.copy())) {
                    append(AnnotationElements.read(reader))
                }
                val end = reader.take()
                if (end != ')') {
                    throw IllegalArgumentException("Invalid annotation, expected ), found $end")
                }
                append(')')
                if (Invisible.shouldRead(reader.copy())) {
                    append(Invisible.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation", e)
        }

        override fun unchecked(value: String) = Annotation(value)

    }

    fun getParts(): Triple<ObjectType, AnnotationElements?, Invisible?> = StringCharReader(value.substring(1)).let {
        val obj = ObjectType.read(it)
        val open = it.take()
        if (open != '(') {
            throw IllegalArgumentException("Invalid annotation, expected (, found $open")
        }
        val elements = if (AnnotationElements.shouldRead(it.copy())) {
            AnnotationElements.read(it)
        } else {
            null
        }
        val close = it.take()
        if (close != ')') {
            throw IllegalArgumentException("Invalid annotation, expected ), found $close")
        }
        val invis = if (Invisible.shouldRead(it.copy())) {
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