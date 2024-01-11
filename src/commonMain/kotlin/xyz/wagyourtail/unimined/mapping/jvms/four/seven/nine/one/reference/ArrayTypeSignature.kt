package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.JavaTypeSignature
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

    override fun toString() = value

}
