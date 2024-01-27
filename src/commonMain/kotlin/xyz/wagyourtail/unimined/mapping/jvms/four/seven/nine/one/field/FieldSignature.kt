package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * FieldSignature:
 *   [ReferenceTypeSignature]
 */
@JvmInline
value class FieldSignature private constructor(val value: ReferenceTypeSignature) {

    companion object: TypeCompanion<FieldSignature> {
        override fun shouldRead(reader: CharReader) = ReferenceTypeSignature.shouldRead(reader)

        override fun read(reader: CharReader) =
            try {
                FieldSignature(ReferenceTypeSignature.read(reader))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid field signature", e)
            }

        override fun unchecked(value: String) = FieldSignature(ReferenceTypeSignature.unchecked(value))

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}