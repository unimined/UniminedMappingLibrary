package xyz.wagyourtail.unimined.mapping.jvms.signature.method

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.signature.`class`.TypeParameters
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * MethodSignature:
 *   [[TypeParameters]] ( {[JavaTypeSignature]} ) [Result] {[ThrowsSignature]}
 */
@JvmInline
value class MethodSignature private constructor(val value: String) {

    companion object: TypeCompanion<MethodSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            if (TypeParameters.shouldRead(reader.peek())) {
                return TypeParameters.shouldRead(reader)
            }
            return reader.readUtf8CodePoint().checkedToChar() == '('
        }

        override fun read(reader: BufferedSource): MethodSignature {
            try {
                return MethodSignature(buildString {
                    if (TypeParameters.shouldRead(reader.peek())) {
                        append(TypeParameters.read(reader))
                    }
                    if (reader.readUtf8CodePoint().checkedToChar() != '(') {
                        throw IllegalArgumentException("Invalid method signature")
                    }
                    append('(')
                    while (true) {
                        if (reader.peek().readUtf8CodePoint().checkedToChar() == ')') {
                            reader.readUtf8CodePoint()
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

        fun create(typeParams: List<String>?, params: List<String>, returnType: String, throws: List<String>) = MethodSignature(buildString {
            if (typeParams != null) {
                append(TypeParameters.read("<${typeParams.joinToString("")}>"))
            }
            append('(')
            params.forEach { append(JavaTypeSignature.read(it)) }
            append(')')
            append(Result.read(returnType))
            throws.forEach { append(ThrowsSignature.read("^$it")) }
        })
    }

    fun getParts(): Pair<TypeParameters?, Triple<List<JavaTypeSignature>, Result, List<ThrowsSignature>>> {
        Buffer().use {
            it.writeUtf8(value)
            val typeParams = if (TypeParameters.shouldRead(it.peek())) {
                TypeParameters.read(it)
            } else null
            if (it.readUtf8CodePoint().checkedToChar() != '(') {
                throw IllegalArgumentException("Invalid method signature")
            }
            val params = mutableListOf<JavaTypeSignature>()
            while (true) {
                val value = it.peek().readUtf8CodePoint().checkedToChar()
                if (value == ')') {
                    it.readUtf8CodePoint()
                    break
                }
                params.add(JavaTypeSignature.read(it))
            }
            val result = Result.read(it)
            val throws = mutableListOf<ThrowsSignature>()
            while (!it.exhausted()) {
                throws.add(ThrowsSignature.read(it))
            }
            return Pair(typeParams, Triple(params, result, throws))
        }
    }

    override fun toString() = value

}