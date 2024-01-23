package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * NameAndDescriptor
 *  [UnqualifiedName] [; [FieldOrMethodDescriptor]]
 */
@JvmInline
value class NameAndDescriptor(val value: String) {

    constructor(name: UnqualifiedName, descriptor: FieldOrMethodDescriptor?) : this(buildString {
        append(name)
        if (descriptor != null) {
            append(';')
            append(descriptor)
        }
    })

    companion object : TypeCompanion<NameAndDescriptor> {
        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllagalChars
        }

        override fun read(reader: CharReader) = try {
            NameAndDescriptor(buildString {
                append(UnqualifiedName.read(reader))
                if (!reader.exhausted() && reader.peek() == ';') {
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