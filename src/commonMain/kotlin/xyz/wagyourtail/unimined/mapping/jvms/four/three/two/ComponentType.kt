package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ComponentType:
 *   FieldType
 */
@JvmInline
value class ComponentType private constructor(val value: FieldType) {

    companion object: TypeCompanion<ComponentType> {

        override fun shouldRead(reader: CharReader) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader) = try {
            ComponentType(FieldType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid component type", e)
        }

        override fun unchecked(value: String) = ComponentType(FieldType.unchecked(value))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()
}