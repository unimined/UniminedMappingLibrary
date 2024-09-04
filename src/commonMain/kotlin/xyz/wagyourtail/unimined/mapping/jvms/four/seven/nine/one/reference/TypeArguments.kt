package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * TypeArguments:
 *   < [TypeArgument] {[TypeArgument]} >
 */
@JvmInline
value class TypeArguments private constructor(val value: String) {

    companion object: TypeCompanion<TypeArguments> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '<'
        }

        override fun read(reader: CharReader<*>): TypeArguments {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type arguments")
            }
            try {
                return TypeArguments(buildString {
                    append('<')
                    while (true) {
                        append(TypeArgument.read(reader))
                        if (reader.peek() == '>') {
                            reader.take()
                            break
                        }
                    }
                    append('>')
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid type arguments", e)
            }
        }

        override fun unchecked(value: String) = TypeArguments(value)
    }

    fun getParts(): List<TypeArgument> = StringCharReader(value.substring(1, value.length - 1)).let {
        val args = mutableListOf<TypeArgument>()
        while (true) {
            args.add(TypeArgument.read(it))
            if (it.exhausted()) {
                break
            }
        }
        return args
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