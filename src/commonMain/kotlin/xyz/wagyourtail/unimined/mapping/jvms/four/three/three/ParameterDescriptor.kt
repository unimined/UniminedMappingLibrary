package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ParameterDescriptor:
 *   [FieldType]
 */
@JvmInline
value class ParameterDescriptor private constructor(val value: FieldType) : Type {

    companion object: TypeCompanion<ParameterDescriptor> {

        override fun shouldRead(reader: CharReader<*>) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader<*>) = ParameterDescriptor(FieldType.read(reader))

        override fun unchecked(value: String) = ParameterDescriptor(FieldType.unchecked(value))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}