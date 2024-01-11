package xyz.wagyourtail.unimined.mapping.jvms.signature.method

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.TypeVariableSignature
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ThrowsSignature:
 *   ^ [ClassTypeSignature]
 *   ^ [TypeVariableSignature]
 */
@JvmInline
value class ThrowsSignature private constructor(val value: String) {

    companion object: TypeCompanion<ThrowsSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '^'
        }

        override fun read(reader: BufferedSource): ThrowsSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid throws signature")
            }
            return ThrowsSignature(buildString {
                append('^')
                if (ClassTypeSignature.shouldRead(reader.peek())) {
                    append(ClassTypeSignature.read(reader))
                } else {
                    append(TypeVariableSignature.read(reader))
                }
            })
        }
    }

    fun isClassTypeSignature() = value[1] == 'L'

    fun isTypeVariableSignature() = value[1] == 'T'

    fun getClassTypeSignature() = ClassTypeSignature.read(value.substring(1))

    fun getTypeVariableSignature() = TypeVariableSignature.read(value.substring(1))

    override fun toString() = value

}