package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * FieldDescriptor:
 *   [FieldType]
 */
@JvmInline
value class FieldDescriptor(val value: FieldType) {

    companion object: TypeCompanion<FieldDescriptor> {
        override fun shouldRead(reader: BufferedSource) = FieldType.shouldRead(reader)

        override fun read(reader: BufferedSource) = try {
            FieldDescriptor(FieldType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field descriptor", e)
        }
    }

    override fun toString() = value.toString()
}