package xyz.wagyourtail.unimined.mapping.jvms.ext.condition

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * AccessConditions:
 *   *
 *   {[AccessCondition]}
 */
@JvmInline
value class AccessConditions private constructor(val value: String) : Type {

    companion object : TypeCompanion<AccessConditions> {
        val ALL = AccessConditions("*")

        override fun shouldRead(reader: CharReader<*>): Boolean {
            val peek = reader.peek()
            return peek == '*' || AccessCondition.shouldRead(reader.copy())
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val peek = reader.peek()
            if (peek == '*') {
                append(reader.take()!!)
                return
            }
            while (AccessCondition.shouldRead(reader.copy())) {
                append(AccessCondition.read(reader))
            }
        }

        override fun unchecked(value: String) = AccessConditions(value)

    }

    fun getParts(): List<AccessCondition> {
        if (value == "*") {
            return emptyList()
        }
        val parts = mutableListOf<AccessCondition>()
        StringCharReader(value).let {
            while (true) {
                parts.add(AccessCondition.read(it))
                if (it.exhausted()) {
                    break
                }
            }
        }
        return parts
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            for (part in getParts()) {
                part.accept(visitor)
            }
        }
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