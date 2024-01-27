package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.TypeVariableSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
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

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '^'
        }

        override fun read(reader: CharReader): ThrowsSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid throws signature")
            }
            return ThrowsSignature(buildString {
                append('^')
                if (ClassTypeSignature.shouldRead(reader.copy())) {
                    append(ClassTypeSignature.read(reader))
                } else {
                    append(TypeVariableSignature.read(reader))
                }
            })
        }

        override fun unchecked(value: String) = ThrowsSignature(value)
    }

    fun isClassTypeSignature() = value[1] == 'L'

    fun isTypeVariableSignature() = value[1] == 'T'

    fun getClassTypeSignature() = ClassTypeSignature.unchecked(value.substring(1))

    fun getTypeVariableSignature() = TypeVariableSignature.unchecked(value.substring(1))

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("^", true)
            if (isClassTypeSignature()) {
                getClassTypeSignature().accept(visitor)
            } else {
                getTypeVariableSignature().accept(visitor)
            }
        }
    }

    override fun toString() = value

}