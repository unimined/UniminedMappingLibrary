package xyz.wagyourtail.unimined.mapping.jvms.ext

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor

/**
 * NameAndDescriptor
 *  [UnqualifiedName] [; [FieldOrMethodDescriptor]]
 */
interface NameAndDescriptor : Type {

    companion object : TypeCompanion<NameAndDescriptor> {

        operator fun invoke(name: UnqualifiedName, descriptor: FieldOrMethodDescriptor): NameAndDescriptor {
            if (descriptor is MethodDescriptor) return name.withMethodDesc(descriptor)
            return name.withFieldDesc(descriptor as FieldDescriptor)
        }

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(UnqualifiedName.read(reader))
            if (!reader.exhausted() && reader.peek() == ';') {
                append(reader.expect(';'))
                append(FieldOrMethodDescriptor.read(reader))
            }
        }

        override fun unchecked(value: String) = if (value.contains("(")) {
            MethodNameAndDescriptor(value)
        } else {
            FieldNameAndDescriptor(value)
        }

    }

    val value: String

    val name: UnqualifiedName
        get() = UnqualifiedName.unchecked(value.substringBefore(';'))

    val hasDescriptor: Boolean
        get() = ';' in value

    val descriptor: FieldOrMethodDescriptor?
        get() = if (';' in value) {
            FieldOrMethodDescriptor.unchecked(value.substringAfter(';'))
        } else {
            null
        }

    operator fun component1() = name
    operator fun component2() = descriptor

    fun getParts(): Pair<UnqualifiedName, FieldOrMethodDescriptor?>

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (name, desc) = getParts()
            name.accept(visitor)
            desc?.accept(visitor)
        }
    }

}