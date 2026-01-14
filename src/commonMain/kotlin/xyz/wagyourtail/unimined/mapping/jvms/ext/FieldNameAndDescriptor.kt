package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import kotlin.jvm.JvmInline

/**
 * NameAndDescriptor
 *  [UnqualifiedName] [; [FieldDescriptor]]
 */
@JvmInline
value class FieldNameAndDescriptor(override val value: String) : Type, NameAndDescriptor {

    @Deprecated("use name.withFieldDesc", ReplaceWith("name.withFieldDesc(descriptor)"))
    constructor(name: UnqualifiedName, descriptor: FieldDescriptor?) : this(buildString {
        append(name)
        if (descriptor != null) {
            append(';')
            append(descriptor)
        }
    })

    companion object : TypeCompanion<FieldNameAndDescriptor> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val name = UnqualifiedName.read(reader)
            append(name)
            if (!reader.exhausted() && reader.peek() == ';') {
                append(reader.expect(';'))
                append(FieldDescriptor.read(reader))
            }
        }

        override fun unchecked(value: String) = FieldNameAndDescriptor(value)

    }

    override val descriptor: FieldDescriptor? get() = if (';' in value) {
        FieldDescriptor.unchecked(value.substringAfter(';'))
    } else {
        null
    }

    override operator fun component1() = name
    override operator fun component2() = descriptor

    override fun getParts(): Pair<UnqualifiedName, FieldDescriptor?> {
        val name = value.substringBefore(';')
        val desc = if (';' in value) {
            FieldDescriptor.unchecked(value.substringAfter(';'))
        } else {
            null
        }
        return UnqualifiedName.unchecked(name) to desc
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (name, desc) = getParts()
            name.accept(visitor)
            desc?.accept(visitor)
        }
    }

    fun asNameAndDescriptor(): NameAndDescriptor = NameAndDescriptor.unchecked(value)

    override fun toString() = value

}
