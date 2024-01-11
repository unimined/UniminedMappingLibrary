package xyz.wagyourtail.unimined.mapping.jvms.descriptor.method

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * MethodDescriptor:
 *   ( {[ParameterDescriptor]} ) [ReturnDescriptor]
 */
@JvmInline
value class MethodDescriptor private constructor(val value: String) {

    companion object: TypeCompanion<MethodDescriptor> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '('
        }

        override fun read(reader: BufferedSource): MethodDescriptor {
            try {
                if (!shouldRead(reader)) {
                    throw IllegalArgumentException("Invalid method type")
                }
                return MethodDescriptor(buildString {
                    append('(')
                    while (true) {
                        if (reader.peek().readUtf8CodePoint().checkedToChar() == ')') {
                            reader.readUtf8CodePoint()
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

    fun getParts(): Pair<ReturnDescriptor, List<ParameterDescriptor>> = Buffer().use {
        it.writeUtf8(value.substring(1))
        val params = mutableListOf<ParameterDescriptor>()
        while (true) {
            val value = it.peek().readUtf8CodePoint().checkedToChar()
            if (value == ')') {
                it.readUtf8CodePoint()
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

    override fun toString() = value

}