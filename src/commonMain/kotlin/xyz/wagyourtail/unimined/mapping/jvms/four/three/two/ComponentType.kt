package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * ComponentType:
 *   FieldType
 */
@JvmInline
value class ComponentType private constructor(val value: FieldType) {

    companion object: TypeCompanion<ComponentType> {

        override fun shouldRead(reader: BufferedSource) = FieldType.shouldRead(reader)

        override fun read(reader: BufferedSource) = try {
            ComponentType(FieldType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid component type", e)
        }
    }

    override fun toString() = value.toString()
}