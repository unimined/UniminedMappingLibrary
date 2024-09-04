package xyz.wagyourtail.unimined.mapping.jvms.ext.condition

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * AccessConditions:
 *   *
 *   {[AccessCondition]}
 */
@JvmInline
value class Access private constructor(val value: String) {

    companion object : TypeCompanion<Access> {
        val flags = AccessFlag.entries.map { it.name.lowercase() }

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return true
        }

        override fun read(reader: CharReader<*>): Access {
            val accessName = reader.takeWhile { it.isLetter() }
            if (accessName.lowercase() !in flags) {
                throw IllegalArgumentException("Invalid access flag: $accessName")
            }
            return Access(accessName)
        }

        override fun unchecked(value: String) = Access(value)

    }

    fun getAccessFlag(): AccessFlag {
        return AccessFlag.valueOf(value.uppercase())
    }

}