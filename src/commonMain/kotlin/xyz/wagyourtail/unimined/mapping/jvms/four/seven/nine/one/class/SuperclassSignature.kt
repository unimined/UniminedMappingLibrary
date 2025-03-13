package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * SuperclassSignature:
 *   [ClassTypeSignature]
 */
@JvmInline
value class SuperclassSignature private constructor(val value: ClassTypeSignature) : Type {

    companion object: TypeCompanion<SuperclassSignature> {

        override fun shouldRead(reader: CharReader<*>) = ClassTypeSignature.shouldRead(reader)

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(ClassTypeSignature.read(reader))
        }

        override fun unchecked(value: String) = SuperclassSignature(ClassTypeSignature.unchecked(value))

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}