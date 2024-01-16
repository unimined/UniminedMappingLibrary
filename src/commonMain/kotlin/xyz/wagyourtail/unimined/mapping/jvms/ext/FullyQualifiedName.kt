package xyz.wagyourtail.unimined.mapping.jvms.ext

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * FullyQualifiedName:
 *  [ObjectType] [[NameAndDescriptor]]
 */
@JvmInline
value class FullyQualifiedName(val value: String) {

    companion object : TypeCompanion<FullyQualifiedName> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == 'L'
        }

        override fun read(reader: BufferedSource) = try {
            FullyQualifiedName(buildString {
                append(ObjectType.read(reader))
                if (!reader.exhausted() && NameAndDescriptor.shouldRead(reader.peek())) {
                    append(NameAndDescriptor.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid fully qualified member type", e)
        }

    }

    fun getParts(): Pair<ObjectType, NameAndDescriptor?> = Buffer().use {
        it.writeUtf8(value)
        val objectType = ObjectType.read(it)
        if (it.exhausted()) {
            objectType to null
        } else {
            objectType to NameAndDescriptor.read(it)
        }
    }

    override fun toString() = value

}