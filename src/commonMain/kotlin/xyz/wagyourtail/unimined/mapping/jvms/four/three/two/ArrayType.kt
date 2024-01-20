package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
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
            return ArrayType("[${ComponentType.read(reader)}")
        }
    }

    fun getParts(): ComponentType {
        return ComponentType.read(value.substring(1))
    }

    fun getDimensionsAndComponent(): Pair<Int, ComponentType> {
        val component = value.trimStart('[')
        val dimensions = value.length - component.length
        return Pair(dimensions, ComponentType.read(component))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("[", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value
}