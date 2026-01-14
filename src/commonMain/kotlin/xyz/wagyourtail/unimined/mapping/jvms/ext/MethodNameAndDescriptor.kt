package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import kotlin.jvm.JvmInline

/**
 * NameAndDescriptor
 *  [UnqualifiedName] [; [MethodDescriptor]]
 */
@JvmInline
value class MethodNameAndDescriptor(override val value: String) : Type, NameAndDescriptor {

    @Deprecated("use name.withMethodDesc", ReplaceWith("name.withMethodDesc(descriptor)"))
    constructor(name: UnqualifiedName, descriptor: MethodDescriptor?) : this(buildString {
        append(name)
        if (descriptor != null) {
            append(';')
            append(descriptor)
        }
    })

    companion object : TypeCompanion<MethodNameAndDescriptor> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val name = UnqualifiedName.read(reader)
            JVMS.checkMethodName(name)

            append(name)
            if (!reader.exhausted() && reader.peek() == ';') {
                append(reader.expect(';'))
                append(MethodDescriptor.read(reader))
            }
        }

        override fun unchecked(value: String) = MethodNameAndDescriptor(value)

    }

    override val descriptor: MethodDescriptor?
        get() = if (';' in value) {
            MethodDescriptor.unchecked(value.substringAfter(';'))
        } else {
            null
        }

    override operator fun component1() = name
    override operator fun component2() = descriptor

    override fun getParts(): Pair<UnqualifiedName, MethodDescriptor?> {
        val name = value.substringBefore(';')
        val desc = if (';' in value) {
            MethodDescriptor.unchecked(value.substringAfter(';'))
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