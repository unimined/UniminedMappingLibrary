/**
 * This file implements JVMS 4.3.3
 * https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3.3
 */
package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.toUnicode
import kotlin.jvm.JvmInline

/**
 * VoidDescriptor:
 *   V
 */
@JvmInline
value class VoidDescriptor private constructor(val value: Char) {

    companion object: TypeCompanion<VoidDescriptor> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() == 'V'
        }

        override fun read(reader: CharReader): VoidDescriptor {
            val value = reader.take()
            if (value != 'V') {
                throw IllegalArgumentException("Invalid void type: $value")
            }
            return VoidDescriptor(value)
        }

        override fun unchecked(value: String) = VoidDescriptor(value.toCharArray()[0])
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value.toString()

}