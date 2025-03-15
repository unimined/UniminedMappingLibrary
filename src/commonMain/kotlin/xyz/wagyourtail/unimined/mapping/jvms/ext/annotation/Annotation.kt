package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * Annotation:
 *   @ [ObjectType] ( [[AnnotationElements]] ) [[Invisible]]
 */
@JvmInline
value class Annotation private constructor(val value: String) : Type {

    companion object: TypeCompanion<Annotation> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '@'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('@'))
            append(ObjectType.read(reader))
            append(reader.expect('('))
            if (AnnotationElements.shouldRead(reader.copy())) {
                append(AnnotationElements.read(reader))
            }
            append(reader.expect(')'))
            if (Invisible.shouldRead(reader.copy())) {
                append(Invisible.read(reader))
            }
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (obj, elements, invis) = getParts()
            visitor("@")
            obj.accept(visitor)
            visitor("(")
            elements?.accept(visitor)
            visitor(")")
            invis?.accept(visitor)
        }
    }

    override fun toString() = value

}