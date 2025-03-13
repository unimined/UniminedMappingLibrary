package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ClassBound:
 *   : [[ReferenceTypeSignature]]
 */
@JvmInline
value class ClassBound private constructor(val value: String) : Type {

    companion object: TypeCompanion<ClassBound> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == ':'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(':')
            if (ReferenceTypeSignature.shouldRead(reader.copy())) {
                append(ReferenceTypeSignature.read(reader))
            }
        }

        override fun unchecked(value: String) = ClassBound(value)

    }

    fun getParts(): ReferenceTypeSignature? {
        if (value.length == 1) return null
        return ReferenceTypeSignature.unchecked(value.substring(1))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(":")
            getParts()?.accept(visitor)
        }
    }

    override fun toString() = value

}