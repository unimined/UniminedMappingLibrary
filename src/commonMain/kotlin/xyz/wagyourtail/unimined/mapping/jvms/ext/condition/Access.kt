package xyz.wagyourtail.unimined.mapping.jvms.ext.condition

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * Access:
 *   Public
 *   Private
 *   etc.
 */
@JvmInline
value class Access private constructor(val value: String) : Type {

    companion object : TypeCompanion<Access> {
        val flags = AccessFlag.entries.map { it.name.lowercase() }

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val accessName = reader.takeWhile { it.isLetter() }
            if (accessName.lowercase() !in flags) {
                throw IllegalArgumentException("Invalid access flag: $accessName")
            }
            append(accessName)
        }

        override fun unchecked(value: String) = Access(value)

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    fun getAccessFlag(): AccessFlag {
        return AccessFlag.valueOf(value.uppercase())
    }

    override fun toString() = value

}