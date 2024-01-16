package xyz.wagyourtail.unimined.mapping.jvms.ext

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.expect
import kotlin.jvm.JvmInline

/**
 * NameAndDescriptor
 *  [UnqualifiedName] [; [FieldOrMethodDescriptor]]
 */
@JvmInline
value class NameAndDescriptor(val value: String) {

    companion object : TypeCompanion<NameAndDescriptor> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() !in JVMS.unqualifiedNameIllagalChars
        }

        override fun read(reader: BufferedSource) = try {
            NameAndDescriptor(buildString {
                append(UnqualifiedName.read(reader))
                if (!reader.exhausted() && reader.peek().readUtf8CodePoint().checkedToChar() == ';') {
                    append(reader.expect(';'))
                    append(FieldOrMethodDescriptor.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid fully qualified member type", e)
        }
    }

    fun getParts(): Pair<UnqualifiedName, FieldOrMethodDescriptor?> {
        val name = value.substringBefore(';')
        val desc = if (';' in value) {
            FieldOrMethodDescriptor.read(value.substringAfter(';'))
        } else {
            null
        }
        return UnqualifiedName.read(name) to desc
    }

    override fun toString() = value



}