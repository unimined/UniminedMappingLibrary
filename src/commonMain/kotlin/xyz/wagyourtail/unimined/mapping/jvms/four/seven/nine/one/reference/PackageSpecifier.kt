package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Identifier
import kotlin.jvm.JvmInline

/**
 * PackageSpecifier:
 *   [Identifier] / {[PackageSpecifier]}
 */
@JvmInline
value class PackageSpecifier private constructor(val value: String) : Type {

    companion object: TypeCompanion<PackageSpecifier> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            // read ahead to make sure there's a trailing / after a bunch of identifier characters
            reader.takeUntil { it in JVMS.identifierIllegalChars }
            if (reader.exhausted()) {
                return false
            }
            return reader.take() == '/'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            while (shouldRead(reader.copy())) {
                append(Identifier.read(reader))
                append(reader.expect('/'))
            }
        }

        override fun unchecked(value: String) = PackageSpecifier(value)
    }

    fun getParts(): List<Identifier> {
        return value.split("/").map { Identifier.unchecked(it) }
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