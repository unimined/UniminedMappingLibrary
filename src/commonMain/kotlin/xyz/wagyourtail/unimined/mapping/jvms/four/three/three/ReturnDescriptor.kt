package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
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

        override fun shouldRead(reader: BufferedSource) =
            innerTypes.firstOrNull { it.shouldRead(reader.peek()) }?.shouldRead(reader) == true

        override fun read(reader: BufferedSource) = try {
            ReturnDescriptor(innerTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid return type", e)
        }
    }

    fun isVoidType() = value == "V"

    fun isFieldType() = !isVoidType()

    fun getVoidType() = VoidDescriptor.read(value)

    fun getFieldType() = FieldType.read(value)

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