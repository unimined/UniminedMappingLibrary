package xyz.wagyourtail.unimined.mapping.jvms.ext

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import kotlin.jvm.JvmInline

@JvmInline
value class FieldOrMethodDescriptor private constructor(val value: String) {

    companion object : TypeCompanion<FieldOrMethodDescriptor> {
        val innterTypes = setOf(
            FieldDescriptor,
            MethodDescriptor
        )

        override fun shouldRead(reader: BufferedSource): Boolean {
            return innterTypes.firstOrNull { it.shouldRead(reader.peek()) }?.shouldRead(reader) == true
        }

        override fun read(reader: BufferedSource): FieldOrMethodDescriptor {
            return FieldOrMethodDescriptor(
                innterTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString()
            )
        }

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