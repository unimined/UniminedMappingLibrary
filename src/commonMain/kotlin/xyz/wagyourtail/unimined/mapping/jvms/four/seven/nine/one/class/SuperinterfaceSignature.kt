package xyz.wagyourtail.unimined.mapping.jvms.signature.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.ClassTypeSignature
import kotlin.jvm.JvmInline

/**
 * SuperinterfaceSignature:
 *   [ClassTypeSignature]
 */
@JvmInline
value class SuperinterfaceSignature private constructor(val value: ClassTypeSignature) {

    companion object: TypeCompanion<SuperinterfaceSignature> {

        override fun shouldRead(reader: BufferedSource) = ClassTypeSignature.shouldRead(reader)

        override fun read(reader: BufferedSource) = try {
            SuperinterfaceSignature(ClassTypeSignature.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid superinterface signature", e)
        }

    }

    override fun toString() = value.toString()

}




