package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * InterfaceBound:
 *   : [ReferenceTypeSignature]
 */
@JvmInline
value class InterfaceBound private constructor(val value: String) {

    companion object: TypeCompanion<InterfaceBound> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == ':'
        }

        override fun read(reader: CharReader): InterfaceBound {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid interface bound")
            }
            return InterfaceBound(buildString {
                append(':')
                append(ReferenceTypeSignature.read(reader))
            })
        }

        override fun unchecked(value: String) = InterfaceBound(value)

    }

    fun getParts(): ReferenceTypeSignature = ReferenceTypeSignature.unchecked(value.substring(1))

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(":", true)
            getParts().accept(visitor)
        }
    }

    override fun toString() = value

}