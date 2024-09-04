package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * PackageSpecifier:
 *   Identifier / {[PackageSpecifier]}
 */
@JvmInline
value class PackageSpecifier private constructor(val value: String) {

    companion object: TypeCompanion<PackageSpecifier> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            // read ahead to make sure there's a trailing / after a bunch of identifier characters
            reader.takeUntil { it in JVMS.identifierIllegalChars }
            if (reader.exhausted()) {
                return false
            }
            return reader.take() == '/'
        }

        override fun read(reader: CharReader<*>): PackageSpecifier {
            if (!shouldRead(reader.copy())) {
                throw IllegalArgumentException("Invalid package specifier")
            }
            try {
                return PackageSpecifier(buildString {
                    while (shouldRead(reader.copy())) {
                        append(reader.takeUntil { it in JVMS.identifierIllegalChars })
                        if (reader.exhausted()) break
                        append(reader.take())
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid package specifier", e)
            }
        }

        override fun unchecked(value: String) = PackageSpecifier(value)
    }

    fun getParts(): List<String> {
        return value.split("/")
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val parts = getParts()
            for (i in parts.indices) {
                visitor(parts[i], true)
                if (i != parts.lastIndex) {
                    visitor("/", true)
                }
            }
        }
    }

    override fun toString() = value

}