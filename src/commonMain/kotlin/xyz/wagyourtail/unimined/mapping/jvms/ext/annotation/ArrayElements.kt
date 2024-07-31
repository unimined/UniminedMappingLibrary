package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ArrayElements:
 *   [AnnotationElementValue] , [ArrayElements]
 *   [AnnotationElementValue]
 */
@JvmInline
value class ArrayElements private constructor(val value: String) {

    companion object: TypeCompanion<ArrayElements> {
        override fun shouldRead(reader: CharReader): Boolean {
            return AnnotationElementValue.shouldRead(reader)
        }

        override fun read(reader: CharReader) = try {
            ArrayElements(buildString {
                append(AnnotationElementValue.read(reader))
                while (reader.peek() == ',') {
                    reader.take()
                    append(',')
                    append(AnnotationElementValue.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid array elements", e)
        }

        override fun unchecked(value: String) = ArrayElements(value)
    }

    fun getParts(): List<AnnotationElementValue> = CharReader(value).use {
        val parts = mutableListOf<AnnotationElementValue>()
        while (true) {
            parts.add(AnnotationElementValue.read(it))
            if (it.exhausted() || it.peek() != ',') {
                break
            }
            it.take()
        }
        parts
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val parts = getParts()
            for (i in parts.indices) {
                parts[i].accept(visitor)
                if (i != parts.lastIndex) {
                    visitor(",", true)
                }
            }
        }
    }

    override fun toString() = value

}