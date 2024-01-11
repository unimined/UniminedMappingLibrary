package xyz.wagyourtail.unimined.mapping.jvms.signature.`class`

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * TypeParameters:
 *   < [TypeParameter] {[TypeParameter]} >
 */
@JvmInline
value class TypeParameters private constructor(val value: String) {

    companion object: TypeCompanion<TypeParameters> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '<'
        }

        override fun read(reader: BufferedSource): TypeParameters {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type parameters")
            }
            return TypeParameters(buildString {
                append('<')
                while (true) {
                    if (reader.peek().readUtf8CodePoint().checkedToChar() == '>') {
                        reader.readUtf8CodePoint()
                        break
                    }
                    append(TypeParameter.read(reader))
                }
                append('>')
            })
        }

    }

    fun getParts(): List<TypeParameter> {
        val params = mutableListOf<TypeParameter>()
        Buffer().use {
            it.writeUtf8(value.substring(1, value.length - 1))
            while (!it.exhausted()) {
                params.add(TypeParameter.read(it))
            }
        }
        return params
    }

    override fun toString() = value

}