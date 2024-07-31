package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ReturnDescriptor:
 *   [FieldType]
 *   [VoidDescriptor]
 */
@JvmInline
value class ReturnDescriptor private constructor(val value: String) {

    companion object: TypeCompanion<ReturnDescriptor> {

        private val innerTypes: Set<TypeCompanion<*>> = setOf(FieldType, VoidDescriptor)

        override fun shouldRead(reader: CharReader) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader) = try {
            ReturnDescriptor(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid return type", e)
        }

        override fun unchecked(value: String) = ReturnDescriptor(value)
    }

    fun isVoidType() = value == "V"

    fun isFieldType() = !isVoidType()

    fun getVoidType() = VoidDescriptor.unchecked(value)

    fun getFieldType() = FieldType.unchecked(value)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isVoidType()) {
                getVoidType().accept(visitor)
            } else {
                getFieldType().accept(visitor)
            }
        }
    }

    override fun toString() = value

}