package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
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

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>) = try {
            ReferenceTypeSignature(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid reference type signature", e)
        }

        override fun unchecked(value: String) = ReferenceTypeSignature(value)
    }

    fun isClassTypeSignature() = value.startsWith('L')

    fun isTypeVariableSignature() = value.startsWith('T')

    fun isArrayTypeSignature() = value.startsWith('[')

    fun getClassTypeSignature() = ClassTypeSignature.unchecked(value)

    fun getTypeVariableSignature() = TypeVariableSignature.unchecked(value)

    fun getArrayTypeSignature() = ArrayTypeSignature.unchecked(value)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isClassTypeSignature()) {
                getClassTypeSignature().accept(visitor)
            } else if (isTypeVariableSignature()) {
                getTypeVariableSignature().accept(visitor)
            } else {
                getArrayTypeSignature().accept(visitor)
            }
        }
    }

    override fun toString() = value
}