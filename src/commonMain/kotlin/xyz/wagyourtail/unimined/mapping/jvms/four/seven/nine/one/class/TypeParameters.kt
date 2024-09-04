package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * TypeParameters:
 *   < [TypeParameter] {[TypeParameter]} >
 */
@JvmInline
value class TypeParameters private constructor(val value: String) {

    companion object: TypeCompanion<TypeParameters> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '<'
        }

        override fun read(reader: CharReader<*>): TypeParameters {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type parameters")
            }
            return TypeParameters(buildString {
                append('<')
                while (true) {
                    if (reader.peek() == '>') {
                        reader.take()
                        break
                    }
                    append(TypeParameter.read(reader))
                }
                append('>')
            })
        }

        override fun unchecked(value: String) = TypeParameters(value)

    }

    fun getParts(): List<TypeParameter> = StringCharReader(value.substring(1, value.length - 1)).let {
        val params = mutableListOf<TypeParameter>()
        while (!it.exhausted()) {
            params.add(TypeParameter.read(it))
        }
        params
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("<", true)
            getParts().forEach { it.accept(visitor) }
            visitor(">", true)
        }
    }

    override fun toString() = value

}