package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ArrayElements:
 *   [AnnotationElementValue] , [ArrayElements]
 *   [AnnotationElementValue]
 */
@JvmInline
value class ArrayElements private constructor(val value: String) : Type {

    companion object: TypeCompanion<ArrayElements> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return AnnotationElementValue.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(AnnotationElementValue.read(reader))
            while (reader.peek() == ',') {
                append(reader.take()!!)
                append(AnnotationElementValue.read(reader))
            }
        }

        override fun unchecked(value: String) = ArrayElements(value)
    }

    fun getParts(): List<AnnotationElementValue> = StringCharReader(value).let {
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