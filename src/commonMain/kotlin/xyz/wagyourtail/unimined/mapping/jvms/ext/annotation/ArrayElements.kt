package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayElements:
 *   [AnnotationElementValue] , [ArrayElements]
 *   [AnnotationElementValue]
 */
@JvmInline
value class ArrayElements private constructor(val value: String) {

    companion object: TypeCompanion<ArrayElements> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return AnnotationElementValue.shouldRead(reader)
        }

        override fun read(reader: BufferedSource) = try {
            ArrayElements(buildString {
                append(AnnotationElementValue.read(reader))
                while (reader.peek().readUtf8CodePoint().checkedToChar() == ',') {
                    reader.readUtf8CodePoint()
                    append(',')
                    append(AnnotationElementValue.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid array elements", e)
        }
    }

    fun getParts(): List<AnnotationElementValue> = Buffer().use {
        it.writeUtf8(value)
        val parts = mutableListOf<AnnotationElementValue>()
        while (true) {
            parts.add(AnnotationElementValue.read(it))
            if (it.exhausted() || it.peek().readUtf8CodePoint().checkedToChar() != ',') {
                break
            }
            it.readUtf8CodePoint()
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