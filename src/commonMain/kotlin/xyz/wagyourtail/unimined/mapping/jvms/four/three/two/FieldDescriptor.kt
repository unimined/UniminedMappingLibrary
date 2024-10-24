package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * FieldDescriptor:
 *   [FieldType]
 */
@JvmInline
value class FieldDescriptor(val value: FieldType) {

    companion object: TypeCompanion<FieldDescriptor> {
        override fun shouldRead(reader: CharReader<*>) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader<*>) = try {
            FieldDescriptor(FieldType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field descriptor", e)
        }

        override fun unchecked(value: String) = FieldDescriptor(FieldType.unchecked(value))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()
}