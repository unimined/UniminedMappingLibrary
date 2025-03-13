package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ArrayType:
 *   [ [ComponentType]
 */
@JvmInline
value class ArrayType private constructor(val value: String) : Type {

    companion object: TypeCompanion<ArrayType> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '['
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('['))
            while (reader.peek() == '[') {
                append(reader.take()!!)
            }
            append(ComponentType.read(reader).value)
        }

        override fun unchecked(value: String) = ArrayType(value)
    }

    fun getParts(): Pair<Int, ComponentType> {
        val component = value.trimStart('[')
        val dimensions = value.length - component.length
        return Pair(dimensions, ComponentType.unchecked(component))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val parts = getParts()
            visitor("[".repeat(parts.first))
            parts.second.accept(visitor)
        }
    }

    override fun toString() = value
}