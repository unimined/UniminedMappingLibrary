package xyz.wagyourtail.unimined.mapping.jvms.four.two.one

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * InternalName:
 *   [UnqualifiedName] { / [UnqualifiedName] }
 */
@JvmInline
value class InternalName private constructor(val value: String) {

    companion object: TypeCompanion<InternalName> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() !in JVMS.unqualifiedNameIllagalChars
        }

        override fun read(reader: BufferedSource) = try {
            InternalName(buildString {
                while (true) {
                    append(UnqualifiedName.read(reader))
                    if (reader.exhausted()) {
                        return@buildString
                    }
                    if (reader.peek().readUtf8CodePoint().checkedToChar() != '/') {
                        return@buildString
                    } else {
                        reader.readUtf8CodePoint()
                        append('/')

                    }
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid internal name", e)
        }
    }

    fun getParts(): List<UnqualifiedName> {
        return value.split('/').map { UnqualifiedName.read(it) }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val parts = getParts()
            for (i in parts.indices) {
                parts[i].accept(visitor)
                if (i != parts.size - 1) {
                    visitor("/", true)
                }
            }
        }
    }

    override fun toString() = value

}