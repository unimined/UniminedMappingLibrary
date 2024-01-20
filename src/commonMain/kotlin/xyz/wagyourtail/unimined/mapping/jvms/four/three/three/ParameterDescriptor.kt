package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ParameterDescriptor:
 *   [FieldType]
 */
@JvmInline
value class ParameterDescriptor private constructor(val value: FieldType) {

    companion object: TypeCompanion<ParameterDescriptor> {

        override fun shouldRead(reader: CharReader) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader) = ParameterDescriptor(FieldType.read(reader))

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}