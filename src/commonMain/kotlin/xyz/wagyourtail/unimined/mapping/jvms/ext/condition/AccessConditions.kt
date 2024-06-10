package xyz.wagyourtail.unimined.mapping.jvms.ext.condition

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * AccessConditions:
 *   *
 *   {[AccessCondition]}
 */
@JvmInline
value class AccessConditions private constructor(val value: String) {

    companion object : TypeCompanion<AccessConditions> {
        val ALL = AccessConditions("*")

        override fun shouldRead(reader: CharReader): Boolean {
            val peek = reader.peek()
            return peek == '*' || AccessCondition.shouldRead(reader.copy())
        }

        override fun read(reader: CharReader) = AccessConditions(buildString {
            val peek = reader.take()
            if (peek == '*') {
                append(peek)
                return@buildString
            }
            while (AccessCondition.shouldRead(reader.copy())) {
                append(AccessCondition.read(reader))
            }
        })

        override fun unchecked(value: String) = AccessConditions(value)

    }

    fun getParts(): List<AccessCondition> {
        if (value == "*") {
            return emptyList()
        }
        val parts = mutableListOf<AccessCondition>()
        CharReader(value).use {
            while (true) {
                parts.add(AccessCondition.read(it))
                if (it.exhausted()) {
                    break
                }
            }
        }
        return parts
    }

    override fun toString() = value

    fun check(access: Set<AccessFlag>): Boolean {
        val parts = getParts()
        for (part in parts) {
            val (req, flag) = part.getParts()
            if (req == AccessCondition.Requirement.CONTAINS) {
                if (!access.contains(flag.getAccessFlag())) {
                    return false
                }
            } else {
                if (access.contains(flag.getAccessFlag())) {
                    return false
                }
            }
        }
        return true
    }

}