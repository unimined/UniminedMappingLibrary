package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ArrayTypeSignature:
 *   [ [JavaTypeSignature]
 */
@JvmInline
value class ArrayTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<ArrayTypeSignature> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == '['
        }

        override fun read(reader: CharReader): ArrayTypeSignature {
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

        override fun unchecked(value: String) = ArrayTypeSignature(value)
    }

    fun getParts(): JavaTypeSignature {
        return JavaTypeSignature.unchecked(value.substring(1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("[", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}
