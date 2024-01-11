package xyz.wagyourtail.unimined.mapping.jvms.signature.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.ReferenceTypeSignature
import kotlin.jvm.JvmInline

/**
 * FieldSignature:
 *   [ReferenceTypeSignature]
 */
@JvmInline
value class FieldSignature private constructor(val value: ReferenceTypeSignature) {

    companion object: TypeCompanion<FieldSignature> {
        override fun shouldRead(reader: BufferedSource) = ReferenceTypeSignature.shouldRead(reader)

        override fun read(reader: BufferedSource) =
            try {
                FieldSignature(ReferenceTypeSignature.read(reader))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid field signature", e)
            }

    }

    override fun toString() = value.toString()

}