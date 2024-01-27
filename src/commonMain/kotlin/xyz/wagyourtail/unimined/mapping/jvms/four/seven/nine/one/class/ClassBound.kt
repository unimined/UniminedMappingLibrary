package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ClassBound:
 *   : [[ReferenceTypeSignature]]
 */
@JvmInline
value class ClassBound private constructor(val value: String) {

    companion object: TypeCompanion<ClassBound> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == ':'
        }

        override fun read(reader: CharReader): ClassBound {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid class bound")
            }
            return ClassBound(buildString {
                append(':')
                if (ReferenceTypeSignature.shouldRead(reader.copy())) {
                    append(ReferenceTypeSignature.read(reader))
                }
            })
        }

        override fun unchecked(value: String) = ClassBound(value)

    }

    fun getParts(): ReferenceTypeSignature? {
        if (value.length == 1) return null
        return ReferenceTypeSignature.unchecked(value.substring(1))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor(":", true)
            getParts()?.accept(visitor)
        }
    }

    override fun toString() = value

}