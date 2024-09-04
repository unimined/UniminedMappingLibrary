package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * AnnotationElement:
 *   [AnnotationElementName] = [AnnotationElementValue]
 */

@JvmInline
value class AnnotationElement private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElement> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AnnotationElementName.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            AnnotationElement(buildString {
                append(AnnotationElementName.read(reader))
                val next = reader.take()
                if (next != '=') {
                    throw IllegalArgumentException("Invalid annotation element, expected =, found $next")
                }
                append('=')
                append(AnnotationElementValue.read(reader))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element", e)
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

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val (name, value) = getParts()
            name.accept(visitor)
            visitor("=", true)
            value.accept(visitor)
        }
    }

    override fun toString() = value

}