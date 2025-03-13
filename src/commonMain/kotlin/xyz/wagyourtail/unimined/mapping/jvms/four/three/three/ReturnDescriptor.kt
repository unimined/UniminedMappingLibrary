package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ReturnDescriptor:
 *   [FieldType]
 *   [VoidDescriptor]
 */
@JvmInline
value class ReturnDescriptor private constructor(val value: String) : Type {

    companion object: TypeCompanion<ReturnDescriptor> {

        private val innerTypes: Set<TypeCompanion<*>> = setOf(FieldType, VoidDescriptor)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String) = ReturnDescriptor(value)
    }

    fun isVoidType() = value == "V"

    fun isFieldType() = !isVoidType()

    fun getVoidType() = VoidDescriptor.unchecked(value)

    fun getFieldType() = FieldType.unchecked(value)

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isVoidType()) {
                getVoidType().accept(visitor)
            } else {
                getFieldType().accept(visitor)
            }
        }
    }

    override fun toString() = value

}