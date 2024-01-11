package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * ReferenceTypeSignature:
 *   [ClassTypeSignature]
 *   [TypeVariableSignature]
 *   [ArrayTypeSignature]
 */
@JvmInline
value class ReferenceTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<ReferenceTypeSignature> {
        val innerTypes: Set<TypeCompanion<*>> = setOf(ClassTypeSignature, TypeVariableSignature, ArrayTypeSignature)

        override fun shouldRead(reader: BufferedSource) =
            innerTypes.firstOrNull { it.shouldRead(reader.peek()) }?.shouldRead(reader) == true

        override fun read(reader: BufferedSource) = try {
            ReferenceTypeSignature(innerTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid reference type signature", e)
        }
    }

    fun isClassTypeSignature() = value.startsWith('L')

    fun isTypeVariableSignature() = value.startsWith('T')

    fun isArrayTypeSignature() = value.startsWith('[')

    fun getClassTypeSignature() = ClassTypeSignature.read(value)

    fun getTypeVariableSignature() = TypeVariableSignature.read(value)

    fun getArrayTypeSignature() = ArrayTypeSignature.read(value)

    override fun toString() = value
}