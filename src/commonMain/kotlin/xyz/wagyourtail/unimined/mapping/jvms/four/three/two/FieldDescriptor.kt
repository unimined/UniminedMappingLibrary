package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * FieldDescriptor:
 *   [FieldType]
 */
@JvmInline
value class FieldDescriptor(val value: FieldType) {

    companion object: TypeCompanion<FieldDescriptor> {
        override fun shouldRead(reader: CharReader) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader) = try {
            FieldDescriptor(FieldType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field descriptor", e)
        }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()
}