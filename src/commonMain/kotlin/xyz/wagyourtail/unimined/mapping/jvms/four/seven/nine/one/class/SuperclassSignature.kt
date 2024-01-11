package xyz.wagyourtail.unimined.mapping.jvms.signature.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.ClassTypeSignature
import kotlin.jvm.JvmInline

/**
 * SuperclassSignature:
 *   [ClassTypeSignature]
 */
@JvmInline
value class SuperclassSignature private constructor(val value: ClassTypeSignature) {

    companion object: TypeCompanion<SuperclassSignature> {

        override fun shouldRead(reader: BufferedSource) = ClassTypeSignature.shouldRead(reader)

        override fun read(reader: BufferedSource) = try {
            SuperclassSignature(ClassTypeSignature.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid superclass signature", e)
        }

    }

    override fun toString() = value.toString()

}