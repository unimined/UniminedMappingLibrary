package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * FieldOrMethodDescriptor:
 *   [FieldDescriptor]
 *   [MethodDescriptor]
 */
@JvmInline
value class FieldOrMethodDescriptor private constructor(val value: String) : Type {

    companion object : TypeCompanion<FieldOrMethodDescriptor> {
        val innterTypes = setOf(
            FieldDescriptor,
            MethodDescriptor
        )

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return innterTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>): FieldOrMethodDescriptor {
            return FieldOrMethodDescriptor(
                innterTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString()
            )
        }

        @JvmName("ofField")
        operator fun invoke(descriptor: FieldDescriptor) = FieldOrMethodDescriptor(descriptor.toString())

        @JvmName("ofMethod")
        operator fun invoke(descriptor: MethodDescriptor) = FieldOrMethodDescriptor(descriptor.toString())

        override fun unchecked(value: String) = FieldOrMethodDescriptor(value)
    }

    fun isMethodDescriptor() = value[0] == '('

    fun isFieldDescriptor() = !isMethodDescriptor()

    fun getFieldDescriptor() = if (isFieldDescriptor()) { FieldDescriptor.unchecked(value) } else { error("expected field desc") }

    fun getMethodDescriptor() = if (isMethodDescriptor()) { MethodDescriptor.unchecked(value) } else { error("expected method desc") }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isMethodDescriptor()) {
                getMethodDescriptor().accept(visitor)
            } else {
                getFieldDescriptor().accept(visitor)
            }
        }
    }

    override fun toString() = value

}