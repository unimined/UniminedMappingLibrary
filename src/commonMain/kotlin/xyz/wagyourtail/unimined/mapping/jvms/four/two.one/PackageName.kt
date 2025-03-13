package xyz.wagyourtail.unimined.mapping.jvms.four.two.one

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * PackageName:
 *   [UnqualifiedName] / { [PackageName] }
 */
@JvmInline
value class PackageName private constructor(val value: String) : Type {

    companion object: TypeCompanion<PackageName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val c = reader.takeUntil { it in JVMS.unqualifiedNameIllegalChars }
            if (c.isEmpty()) return false
            return reader.take() == '/'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            while (shouldRead(reader.copy())) {
                append(UnqualifiedName.read(reader))
                append(reader.expect('/'))
            }
        }

        override fun unchecked(value: String) = PackageName(value)
    }

    fun getParts(): List<UnqualifiedName> {
        return value.split("/").map { UnqualifiedName.unchecked(it) }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val parts = getParts()
            for (i in parts.indices) {
                parts[i].accept(visitor)
                if (i != parts.lastIndex) {
                    visitor("/")
                }
            }
        }
    }

    override fun toString() = value

}