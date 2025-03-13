package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ReferenceTypeSignature:
 *   [ClassTypeSignature]
 *   [TypeVariableSignature]
 *   [ArrayTypeSignature]
 */
@JvmInline
value class ReferenceTypeSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<ReferenceTypeSignature> {
        val innerTypes: Set<TypeCompanion<*>> = setOf(ClassTypeSignature, TypeVariableSignature, ArrayTypeSignature)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String) = ReferenceTypeSignature(value)
    }

    fun isClassTypeSignature() = value.startsWith('L')

    fun isTypeVariableSignature() = value.startsWith('T')

    fun isArrayTypeSignature() = value.startsWith('[')

    fun getClassTypeSignature() = ClassTypeSignature.unchecked(value)

    fun getTypeVariableSignature() = TypeVariableSignature.unchecked(value)

    fun getArrayTypeSignature() = ArrayTypeSignature.unchecked(value)

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
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