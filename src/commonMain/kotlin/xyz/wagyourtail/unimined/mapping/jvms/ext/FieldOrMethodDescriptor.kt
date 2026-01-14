package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmName

/**
 * FieldOrMethodDescriptor:
 *   [FieldDescriptor]
 *   [MethodDescriptor]
 */
interface FieldOrMethodDescriptor : Type {

    companion object : TypeCompanion<FieldOrMethodDescriptor> {
        val innerTypes = setOf(
            FieldDescriptor,
            MethodDescriptor
        )

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        @JvmName("ofField")
        @Deprecated("use FieldDescriptor")
        operator fun invoke(descriptor: FieldDescriptor) = descriptor

        @JvmName("ofMethod")
        @Deprecated("use MethodDescriptor")
        operator fun invoke(descriptor: MethodDescriptor) = descriptor

        override fun unchecked(value: String) = if (value[0] == '(') { MethodDescriptor.unchecked(value) } else { FieldDescriptor.unchecked(value) }
    }

    fun isMethodDescriptor() = this is MethodDescriptor

    fun isFieldDescriptor() = this is FieldDescriptor

    fun getFieldDescriptor() = if (isFieldDescriptor()) { this as FieldDescriptor } else { error("expected field desc") }

    fun getMethodDescriptor() = if (isMethodDescriptor()) { this as MethodDescriptor } else { error("expected method desc") }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isMethodDescriptor()) {
                getMethodDescriptor().accept(visitor)
            } else {
                getFieldDescriptor().accept(visitor)
            }
        }
    }

}