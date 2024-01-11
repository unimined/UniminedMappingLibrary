package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * TypeArguments:
 *   < [TypeArgument] {[TypeArgument]} >
 */
@JvmInline
value class TypeArguments private constructor(val value: String) {

    companion object: TypeCompanion<TypeArguments> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '<'
        }

        override fun read(reader: BufferedSource): TypeArguments {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type arguments")
            }
            try {
                return TypeArguments(buildString {
                    append('<')
                    while (true) {
                        append(TypeArgument.read(reader))
                        if (reader.peek().readUtf8CodePoint().checkedToChar() == '>') {
                            reader.readUtf8CodePoint()
                            break
                        }
                    }
                    append('>')
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid type arguments", e)
            }
        }
    }

    fun getParts(): List<TypeArgument> {
        Buffer().use {
            it.writeUtf8(value.substring(1, value.length - 1))
            val args = mutableListOf<TypeArgument>()
            while (true) {
                args.add(TypeArgument.read(it))
                if (it.exhausted()) {
                    break
                }
            }
            return args
        }
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