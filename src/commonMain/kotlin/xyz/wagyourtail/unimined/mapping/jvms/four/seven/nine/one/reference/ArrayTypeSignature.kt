package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayTypeSignature:
 *   [ [JavaTypeSignature]
 */
@JvmInline
value class ArrayTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<ArrayTypeSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == '['
        }

        override fun read(reader: BufferedSource): ArrayTypeSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type value signature")
            }
            try {
                return ArrayTypeSignature(buildString {
                    append('[')
                    append(JavaTypeSignature.read(reader))
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid type value signature", e)
            }
        }
    }

    fun getParts(): JavaTypeSignature {
        return JavaTypeSignature.read(value.substring(1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("[", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}
