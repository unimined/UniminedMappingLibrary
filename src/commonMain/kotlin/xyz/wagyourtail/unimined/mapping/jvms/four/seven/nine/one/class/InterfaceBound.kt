package xyz.wagyourtail.unimined.mapping.jvms.signature.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.signature.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * InterfaceBound:
 *   : [ReferenceTypeSignature]
 */
@JvmInline
value class InterfaceBound private constructor(val value: String) {

    companion object: TypeCompanion<InterfaceBound> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == ':'
        }

        override fun read(reader: BufferedSource): InterfaceBound {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid interface bound")
            }
            return InterfaceBound(buildString {
                append(':')
                append(ReferenceTypeSignature.read(reader))
            })
        }

    }

    fun getParts(): ReferenceTypeSignature = ReferenceTypeSignature.read(value.substring(1))

    override fun toString() = value

}