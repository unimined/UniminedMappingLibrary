package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
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

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(":", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}