package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * FullyQualifiedName:
 *  [ObjectType] [[NameAndDescriptor]]
 */
@JvmInline
value class FullyQualifiedName(val value: String) {

    constructor(type: ObjectType, nameAndDescriptor: NameAndDescriptor?) : this(buildString {
        append(type)
        if (nameAndDescriptor != null) {
            append(nameAndDescriptor)
        }
    })

    companion object : TypeCompanion<FullyQualifiedName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'L'
        }

        override fun read(reader: CharReader<*>) = try {
            FullyQualifiedName(buildString {
                append(ObjectType.read(reader))
                if (!reader.exhausted() && NameAndDescriptor.shouldRead(reader.copy())) {
                    append(NameAndDescriptor.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid fully qualified member type", e)
        }

        override fun unchecked(value: String) = FullyQualifiedName(value)

    }

    fun getParts(): Pair<ObjectType, NameAndDescriptor?> = StringCharReader(value).let {
        val objectType = ObjectType.read(it)
        if (it.exhausted()) {
            objectType to null
        } else {
            objectType to NameAndDescriptor.read(it)
        }
    }

    override fun toString() = value

}