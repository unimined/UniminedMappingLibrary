package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayType:
 *   [ [ComponentType]
 */
@JvmInline
value class ArrayType private constructor(val value: String) {

    companion object: TypeCompanion<ArrayType> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '['
        }

        override fun read(reader: BufferedSource): ArrayType {
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

    override fun toString() = value
}