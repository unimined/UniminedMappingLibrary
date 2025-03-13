package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * MethodDescriptor:
 *   ( {[ParameterDescriptor]} ) [ReturnDescriptor]
 */
@JvmInline
value class MethodDescriptor private constructor(val value: String) : Type {

    companion object: TypeCompanion<MethodDescriptor> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '('
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('('))
            while (reader.peek() != ')') {
                append(ParameterDescriptor.read(reader))
            }
            append(reader.expect(')'))
            append(ReturnDescriptor.read(reader))
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("(")
            val (returnType, params) = getParts()
            params.forEach { it.accept(visitor) }
            visitor(")")
            returnType.accept(visitor)
        }
    }

    override fun toString() = value

}