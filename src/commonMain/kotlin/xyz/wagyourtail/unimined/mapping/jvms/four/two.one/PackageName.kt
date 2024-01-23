package xyz.wagyourtail.unimined.mapping.jvms.four.two.one

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * PackageName:
 *   [UnqualifiedName] / { [PackageName] }
 */
@JvmInline
value class PackageName private constructor(val value: String) {

    companion object: TypeCompanion<PackageName> {
        override fun shouldRead(reader: CharReader): Boolean {
            val c = reader.takeUntil { it in JVMS.unqualifiedNameIllagalChars }
            if (c.isEmpty()) return false
            return reader.take() == '/'
        }

        override fun read(reader: CharReader) = try {
            PackageName(buildString {
                while (shouldRead(reader.copy())) {
                    append(UnqualifiedName.read(reader))
                    val c = reader.take()
                    if (c != '/') {
                        throw IllegalArgumentException("Invalid package name, expected '/' got '${c}'")
                    }
                    append(c)
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid package name", e)
        }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(value, true)
        }
    }

    override fun toString() = value

}