package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * FieldSignature:
 *   [ReferenceTypeSignature]
 */
@JvmInline
value class FieldSignature private constructor(val value: ReferenceTypeSignature) : Type {

    companion object: TypeCompanion<FieldSignature> {
        override fun shouldRead(reader: CharReader<*>) = ReferenceTypeSignature.shouldRead(reader)

        override fun read(reader: CharReader<*>) =
            try {
                FieldSignature(ReferenceTypeSignature.read(reader))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid field signature", e)
            }

        override fun unchecked(value: String) = FieldSignature(ReferenceTypeSignature.unchecked(value))

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}