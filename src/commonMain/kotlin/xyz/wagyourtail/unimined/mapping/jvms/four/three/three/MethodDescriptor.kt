package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * MethodDescriptor:
 *   ( {[ParameterDescriptor]} ) [ReturnDescriptor]
 */
@JvmInline
value class MethodDescriptor private constructor(val value: String) {

    companion object: TypeCompanion<MethodDescriptor> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '('
        }

        override fun read(reader: CharReader): MethodDescriptor {
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

        fun create(returnValue: String, vararg params: String) = MethodDescriptor(buildString {
            append('(')
            params.forEach { append(ParameterDescriptor.read(it)) }
            append(')')
            append(ReturnDescriptor.read(returnValue))
        })

    }

    fun getParts(): Pair<ReturnDescriptor, List<ParameterDescriptor>> = CharReader(value.substring(1)).use {
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