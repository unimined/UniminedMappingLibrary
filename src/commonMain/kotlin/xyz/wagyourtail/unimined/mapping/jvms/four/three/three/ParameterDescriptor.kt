package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
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

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}