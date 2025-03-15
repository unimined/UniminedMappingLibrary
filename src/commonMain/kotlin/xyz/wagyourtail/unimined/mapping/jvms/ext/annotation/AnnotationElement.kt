package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * AnnotationElement:
 *   [AnnotationElementName] = [AnnotationElementValue]
 */

@JvmInline
value class AnnotationElement private constructor(val value: String) : Type {

    companion object: TypeCompanion<AnnotationElement> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AnnotationElementName.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(AnnotationElementName.read(reader))
            append(reader.expect('='))
            append(AnnotationElementValue.read(reader))
        }

        override fun unchecked(value: String) = AnnotationElement(value)
    }

    fun getParts(): Pair<AnnotationElementName, AnnotationElementValue> = StringCharReader(value).let {
        val name = AnnotationElementName.read(it)
        val next = it.take()
        if (next != '=') {
            throw IllegalArgumentException("Invalid annotation element, expected =, found $next")
        }
        val value = AnnotationElementValue.read(it)
        Pair(name, value)
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (name, value) = getParts()
            name.accept(visitor)
            visitor("=")
            value.accept(visitor)
        }
    }

    override fun toString() = value

}