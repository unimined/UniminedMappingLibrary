package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * AnnotationElements:
 *   [AnnotationElements] , [AnnotationElement]
 *   [AnnotationElement]
 */
@JvmInline
value class AnnotationElements private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElements> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return AnnotationElement.shouldRead(reader)
        }

        override fun read(reader: BufferedSource) = try {
            AnnotationElements(buildString {
                append(AnnotationElement.read(reader))
                while (reader.peek().readUtf8CodePoint().checkedToChar() == ',') {
                    reader.readUtf8CodePoint()
                    append(',')
                    append(AnnotationElement.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation elements", e)
        }
    }

    fun getParts(): List<AnnotationElement> = Buffer().use {
        it.writeUtf8(value)
        val parts = mutableListOf<AnnotationElement>()
        while (true) {
            parts.add(AnnotationElement.read(it))
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