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

    fun isSubsetOf(other: AccessConditions): Boolean {
        val parts = getParts().toSet()
        val otherParts = other.getParts().toSet()
        return parts.all { it in otherParts }
    }

    fun isSupersetOf(other: AccessConditions): Boolean {
        val parts = getParts().toSet()
        val otherParts = other.getParts().toSet()
        return otherParts.all { it in parts }
    }

    fun isSubsetOrSupersetOf(other: AccessConditions): Boolean {
        return isSubsetOf(other) || isSupersetOf(other)
    }

    operator fun plus(other: AccessConditions): AccessConditions {
        val parts = getParts().toSet()
        val otherParts = other.getParts().toSet()
        return AccessConditions(parts.union(otherParts).joinToString(""))
    }

    infix fun intersect(other: AccessConditions): AccessConditions {
        val parts = getParts().toSet()
        val otherParts = other.getParts().toSet()
        return AccessConditions(parts.intersect(otherParts).joinToString(""))
    }

    operator fun minus(other: AccessConditions): AccessConditions {
        val parts = getParts().toSet()
        val otherParts = other.getParts().toSet()
        return AccessConditions(parts.minus(otherParts).joinToString(""))
    }



}