package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * FullyQualifiedName:
 *  [ObjectType] [[NameAndDescriptor]]
 */
@JvmInline
value class FullyQualifiedName(val value: String) {

    companion object : TypeCompanion<FullyQualifiedName> {
        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == 'L'
        }

        override fun read(reader: CharReader) = try {
            FullyQualifiedName(buildString {
                append(ObjectType.read(reader))
                if (reader.exhausted() && NameAndDescriptor.shouldRead(reader.copy())) {
                    append(NameAndDescriptor.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid fully qualified member type", e)
        }

    }

    fun getParts(): Pair<ObjectType, NameAndDescriptor?> = CharReader(value).let {
        val objectType = ObjectType.read(it)
        if (it.exhausted()) {
            objectType to null
        } else {
            objectType to NameAndDescriptor.read(it)
        }
    }

    override fun toString() = value

}