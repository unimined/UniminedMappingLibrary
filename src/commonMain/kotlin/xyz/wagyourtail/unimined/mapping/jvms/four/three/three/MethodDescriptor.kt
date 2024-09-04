package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * MethodDescriptor:
 *   ( {[ParameterDescriptor]} ) [ReturnDescriptor]
 */
@JvmInline
value class MethodDescriptor private constructor(val value: String) {

    companion object: TypeCompanion<MethodDescriptor> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '('
        }

        override fun read(reader: CharReader<*>): MethodDescriptor {
            try {
                if (!shouldRead(reader)) {
                    throw IllegalArgumentException("Invalid method type")
                }
                return MethodDescriptor(buildString {
                    append('(')
                    while (true) {
                        if (reader.peek() == ')') {
                            reader.take()
                            break
                        }
                        append(ParameterDescriptor.read(reader))
                    }
                    append(')')
                    append(ReturnDescriptor.read(reader))
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid method type", e)
            }
        }
        override fun unchecked(value: String) = MethodDescriptor(value)

        operator fun invoke(returnValue: String, vararg params: String) = MethodDescriptor(ReturnDescriptor.read(returnValue), params.map { ParameterDescriptor.read(it) })

        operator fun invoke(returnType: ReturnDescriptor, params: List<ParameterDescriptor>) = MethodDescriptor(buildString {
            append('(')
            params.forEach { append(it) }
            append(')')
            append(returnType)
        })

    }

    fun getParts(): Pair<ReturnDescriptor, List<ParameterDescriptor>> = StringCharReader(value.substring(1)).let {
        val params = mutableListOf<ParameterDescriptor>()
        while (true) {
            val value = it.peek()
            if (value == ')') {
                it.take()
                break
            }
            params.add(ParameterDescriptor.read(it))
        }
        val returnType = ReturnDescriptor.read(it)
        if (!it.exhausted()) {
            throw IllegalArgumentException("Invalid method type")
        }
        Pair(returnType, params)
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("(", true)
            val (returnType, params) = getParts()
            params.forEach { it.accept(visitor) }
            visitor(")", true)
            returnType.accept(visitor)
        }
    }

    override fun toString() = value

}