package xyz.wagyourtail.unimined.mapping.jvms.descriptor.method

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.descriptor.field.FieldType
import kotlin.jvm.JvmInline

/**
 * ParameterDescriptor:
 *   [FieldType]
 */
@JvmInline
value class ParameterDescriptor private constructor(val value: FieldType) {

    companion object: TypeCompanion<ParameterDescriptor> {

        override fun shouldRead(reader: BufferedSource) = FieldType.shouldRead(reader)

        override fun read(reader: BufferedSource) = ParameterDescriptor(FieldType.read(reader))

    }

    override fun toString() = value.toString()

}