package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ClassTypeSignatureSuffix:
 *   . [SimpleClassTypeSignature]
 */
@JvmInline
value class ClassTypeSignatureSuffix private constructor(val value: String) {

    companion object: TypeCompanion<ClassTypeSignatureSuffix> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '.'
        }

        override fun read(reader: BufferedSource): ClassTypeSignatureSuffix {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid class type signature suffix")
            }
            try {
                return ClassTypeSignatureSuffix(buildString {
                    append('.')
                    append(SimpleClassTypeSignature.read(reader))
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid class type signature suffix", e)
            }
        }
    }

    fun getParts(): SimpleClassTypeSignature {
        return SimpleClassTypeSignature.read(value.substring(1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(".", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}