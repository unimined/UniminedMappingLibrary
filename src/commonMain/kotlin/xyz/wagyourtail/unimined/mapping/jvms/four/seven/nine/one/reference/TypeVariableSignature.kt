package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * TypeVariableSignature:
 *   T Identifier ;
 */
@JvmInline
value class TypeVariableSignature private constructor(val value: String) {

    companion object: TypeCompanion<TypeVariableSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == 'T'
        }

        override fun read(reader: BufferedSource): TypeVariableSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type variable signature")
            }
            try {
                return TypeVariableSignature(buildString {
                    append('T')
                    val value = reader.takeUTF8Until { it.checkedToChar() in JVMS.identifierIllegalChars }
                    val end = reader.readUtf8CodePoint().checkedToChar()
                    if (end != ';') {
                        throw IllegalArgumentException("Invalid type variable signature: found illegal character: $end")
                    }
                    append(value)
                    append(';')
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid type variable signature", e)
            }
        }
    }

    override fun toString() = value

}