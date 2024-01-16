package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * AnnotationElement:
 *   [AnnotationElementName] = [AnnotationElementValue]
 */

@JvmInline
value class AnnotationElement private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElement> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return AnnotationElementName.shouldRead(reader)
        }

        override fun read(reader: BufferedSource) = try {
            AnnotationElement(buildString {
                append(AnnotationElementName.read(reader))
                val next = reader.readUtf8CodePoint().checkedToChar()
                if (next != '=') {
                    throw IllegalArgumentException("Invalid annotation element, expected =, found $next")
                }
                append('=')
                append(AnnotationElementValue.read(reader))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element", e)
        }
    }

    fun getParts(): Pair<AnnotationElementName, AnnotationElementValue> = Buffer().use {
        it.writeUtf8(value)
        val name = AnnotationElementName.read(it)
        val next = it.readUtf8CodePoint().checkedToChar()
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