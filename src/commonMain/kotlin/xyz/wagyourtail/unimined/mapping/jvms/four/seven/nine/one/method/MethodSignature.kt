package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.TypeParameters
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * MethodSignature:
 *   [[TypeParameters]] ( {[JavaTypeSignature]} ) [Result] {[ThrowsSignature]}
 */
@JvmInline
value class MethodSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<MethodSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (TypeParameters.shouldRead(reader.copy())) {
                return TypeParameters.shouldRead(reader)
            }
            return reader.take() == '('
        }

        override fun read(reader: CharReader<*>): MethodSignature {
            try {
                return MethodSignature(buildString {
                    if (TypeParameters.shouldRead(reader.copy())) {
                        append(TypeParameters.read(reader))
                    }
                    if (reader.take() != '(') {
                        throw IllegalArgumentException("Invalid method signature")
                    }
                    append('(')
                    while (true) {
                        if (reader.peek() == ')') {
                            reader.take()
                            break
                        }
                        append(JavaTypeSignature.read(reader))
                    }
                    append(')')
                    append(Result.read(reader))
                    while (true) {
                        if (reader.exhausted()) {
                            break
                        }
                        append(ThrowsSignature.read(reader))
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid method signature", e)
            }
        }

        fun create(typeParams: List<String>?, params: List<String>, returnType: String, throws: List<String>) =
            MethodSignature(buildString {
                if (typeParams != null) {
                    append(TypeParameters.read("<${typeParams.joinToString("")}>"))
                }
                append('(')
                params.forEach {
                    append(
                        JavaTypeSignature.read(
                            it
                        )
                    )
                }
                append(')')
                append(Result.read(returnType))
                throws.forEach { append(ThrowsSignature.read("^$it")) }
            })

        override fun unchecked(value: String) = MethodSignature(value)
    }

    fun getParts(): Pair<TypeParameters?, Triple<List<JavaTypeSignature>, Result, List<ThrowsSignature>>> = StringCharReader(value).let {
        val typeParams = if (TypeParameters.shouldRead(it.copy())) {
            TypeParameters.read(it)
        } else null
        if (it.take() != '(') {
            throw IllegalArgumentException("Invalid method signature")
        }
        val params = mutableListOf<JavaTypeSignature>()
        while (true) {
            val value = it.peek()
            if (value == ')') {
                it.take()
                break
            }
            params.add(JavaTypeSignature.read(it))
        }
        val result = Result.read(it)
        val throws = mutableListOf<ThrowsSignature>()
        while (!it.exhausted()) {
            throws.add(ThrowsSignature.read(it))
        }
        Pair(typeParams, Triple(params, result, throws))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            getParts().let { (typeParams, values) ->
                val (params, result, throws) = values
                typeParams?.accept(visitor)
                visitor("(")
                params.forEach { it.accept(visitor) }
                visitor(")")
                result.accept(visitor)
                throws.forEach { it.accept(visitor) }
            }
        }
    }

    override fun toString() = value

}