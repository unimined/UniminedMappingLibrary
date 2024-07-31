package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ArrayType:
 *   [ [ComponentType]
 */
@JvmInline
value class ArrayType private constructor(val value: String) {

    companion object: TypeCompanion<ArrayType> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '['
        }

        override fun read(reader: CharReader): ArrayType {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid array type")
            }
            return ArrayType(buildString {
                append("[")
                while (reader.peek() == '[') {
                    append(reader.take())
                }
                append(ComponentType.read(reader).value)
            })
        }

        override fun unchecked(value: String) = ArrayType(value)
    }

    fun getParts(): Pair<Int, ComponentType> {
        val component = value.trimStart('[')
        val dimensions = value.length - component.length
        return Pair(dimensions, ComponentType.unchecked(component))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val parts = getParts()
            visitor("[".repeat(parts.first), true)
            parts.second.accept(visitor)
        }
    }

    override fun toString() = value
}