package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import xyz.wagyourtail.unimined.mapping.util.toUnicode
import kotlin.jvm.JvmInline

/**
 * PackageSpecifier:
 *   Identifier / {[PackageSpecifier]}
 */
@JvmInline
value class PackageSpecifier private constructor(val value: String) {

    companion object: TypeCompanion<PackageSpecifier> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            // read ahead to make sure there's a trailing / after a bunch of identifier characters
            reader.takeUTF8Until { it.checkedToChar() in JVMS.identifierIllegalChars }
            if (reader.exhausted()) {
                return false
            }
            return reader.readUtf8CodePoint().checkedToChar() == '/'
        }

        override fun read(reader: BufferedSource): PackageSpecifier {
            if (!shouldRead(reader.peek())) {
                throw IllegalArgumentException("Invalid package specifier")
            }
            try {
                return PackageSpecifier(buildString {
                    while (shouldRead(reader.peek())) {
                        append(reader.takeUTF8Until { it.checkedToChar() in JVMS.identifierIllegalChars })
                        append(reader.readUtf8CodePoint().toUnicode())
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid package specifier", e)
            }
        }
    }

    fun getParts(): List<PackageSpecifier> {
        return value.split('/').map { PackageSpecifier(it) }
    }

    override fun toString() = value

}