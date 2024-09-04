package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * TypeVariableSignature:
 *   T Identifier ;
 */
@JvmInline
value class TypeVariableSignature private constructor(val value: String) {

    companion object: TypeCompanion<TypeVariableSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'T'
        }

        override fun read(reader: CharReader<*>): TypeVariableSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid type variable signature")
            }
            try {
                return TypeVariableSignature(buildString {
                    append('T')
                    val value = reader.takeUntil { it in JVMS.identifierIllegalChars }
                    val end = reader.take()
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

        override fun unchecked(value: String) = TypeVariableSignature(value)
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value

}