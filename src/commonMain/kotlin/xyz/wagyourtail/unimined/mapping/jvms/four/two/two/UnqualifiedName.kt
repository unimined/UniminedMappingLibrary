package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

@JvmInline
value class UnqualifiedName private constructor(val value: String) {

    companion object: TypeCompanion<UnqualifiedName> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() !in JVMS.unqualifiedNameIllagalChars
        }

        override fun read(reader: BufferedSource) = try {
            val value = reader.takeUTF8Until { it.checkedToChar() in JVMS.unqualifiedNameIllagalChars }
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid unqualified name, cannot be empty")
            }

            UnqualifiedName(value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid unqualified name", e)
        }

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(value, true)
    }

    override fun toString() = value

}