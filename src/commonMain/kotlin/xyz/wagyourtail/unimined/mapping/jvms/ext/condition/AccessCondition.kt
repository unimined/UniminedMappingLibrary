package xyz.wagyourtail.unimined.mapping.jvms.ext.condition

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * AccessCondition:
 *  + [Access]
 *  - [Access]
 */
@JvmInline
value class AccessCondition private constructor(val value: String) {

    companion object : TypeCompanion<AccessCondition> {

        override fun shouldRead(reader: CharReader): Boolean {
            val peek = reader.peek()
            return peek == '+' || peek == '-'
        }

        override fun read(reader: CharReader) = AccessCondition(buildString {
            val peek = reader.take()
            if (peek != '+' && peek != '-') {
                throw IllegalArgumentException("Invalid access condition, expected + or -, found $peek")
            }
            append(peek)
            append(Access.read(reader))
        })

        override fun unchecked(value: String) = AccessCondition(value)

    }

    fun getParts(): Pair<Requirement, Access> {
        return Pair(
            if (value[0] == '+') {
                Requirement.CONTAINS
            } else {
                Requirement.EXCLUDES
            },
            Access.unchecked(value.substring(1))
        )
    }

    override fun toString() = value

    enum class Requirement {
        CONTAINS,
        EXCLUDES
    }

}