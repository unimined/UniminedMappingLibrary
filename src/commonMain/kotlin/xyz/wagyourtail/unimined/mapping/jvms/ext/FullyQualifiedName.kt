package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * FullyQualifiedName:
 *  [ObjectType] [[NameAndDescriptor]]
 */
@JvmInline
value class FullyQualifiedName(val value: String) : Type {

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

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(ObjectType.read(reader))
            if (!reader.exhausted() && NameAndDescriptor.shouldRead(reader.copy())) {
                append(NameAndDescriptor.read(reader))
            }
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (owner, nameAndDesc) = getParts()
            owner.accept(visitor)
            nameAndDesc?.accept(visitor)
        }
    }

    override fun toString() = value

}