package xyz.wagyourtail.unimined.mapping.jvms.ext

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@JvmInline
value class FieldOrMethodDescriptor private constructor(val value: String) {

    companion object : TypeCompanion<FieldOrMethodDescriptor> {
        val innterTypes = setOf(
            FieldDescriptor,
            MethodDescriptor
        )

        override fun shouldRead(reader: CharReader): Boolean {
            return innterTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader): FieldOrMethodDescriptor {
            return FieldOrMethodDescriptor(
                innterTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString()
            )
        }

        @JvmName("ofField")
        operator fun invoke(descriptor: FieldDescriptor) = FieldOrMethodDescriptor(descriptor.toString())

        @JvmName("ofMethod")
        operator fun invoke(descriptor: MethodDescriptor) = FieldOrMethodDescriptor(descriptor.toString())
    }

    fun isMethodDescriptor() = value[0] == '('

    fun isFieldDescriptor() = !isMethodDescriptor()

    fun getFieldDescriptor() = FieldDescriptor.read(value)

    fun getMethodDescriptor() = MethodDescriptor.read(value)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isMethodDescriptor()) {
                getMethodDescriptor().accept(visitor)
            } else {
                getFieldDescriptor().accept(visitor)
            }
        }
    }

    override fun toString() = value

}