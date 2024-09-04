package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

@JvmInline
value class UnqualifiedName private constructor(val value: String) {

    companion object: TypeCompanion<UnqualifiedName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>) = try {
            val value = reader.takeUntil { it in JVMS.unqualifiedNameIllegalChars }
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid unqualified name, cannot be empty")
            }

            UnqualifiedName(value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid unqualified name", e)
        }

        override fun unchecked(value: String) = UnqualifiedName(value)

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(value, true)
    }

    override fun toString() = value

}