package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ClassTypeSignatureSuffix:
 *   . [SimpleClassTypeSignature]
 */
@JvmInline
value class ClassTypeSignatureSuffix private constructor(val value: String) {

    companion object: TypeCompanion<ClassTypeSignatureSuffix> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '.'
        }

        override fun read(reader: CharReader): ClassTypeSignatureSuffix {
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

        override fun unchecked(value: String) = ClassTypeSignatureSuffix(value)
    }

    fun getParts(): SimpleClassTypeSignature {
        return SimpleClassTypeSignature.unchecked(value.substring(1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(".", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}