package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * AnnotationElements:
 *   [AnnotationElements] , [AnnotationElement]
 *   [AnnotationElement]
 */
@JvmInline
value class AnnotationElements private constructor(val value: String) : Type {

    companion object: TypeCompanion<AnnotationElements> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AnnotationElement.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            AnnotationElements(buildString {
                append(AnnotationElement.read(reader))
                while (reader.peek() == ',') {
                    reader.take()
                    append(',')
                    append(AnnotationElement.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation elements", e)
        }

        override fun unchecked(value: String) = AnnotationElements(value)
    }

    fun getParts(): List<AnnotationElement> = StringCharReader(value).let {
        val parts = mutableListOf<AnnotationElement>()
        while (true) {
            parts.add(AnnotationElement.read(it))
            if (it.exhausted() || it.peek() != ',') {
                break
            }
            it.take()
        }
        parts
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val parts = getParts()
            for (i in parts.indices) {
                parts[i].accept(visitor)
                if (i != parts.lastIndex) {
                    visitor(",")
                }
            }
        }
    }

    override fun toString() = value

}