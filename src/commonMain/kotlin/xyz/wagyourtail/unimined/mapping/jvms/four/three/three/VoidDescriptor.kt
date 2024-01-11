/**
 * This file implements JVMS 4.3.3
 * https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3.3
 */
package xyz.wagyourtail.unimined.mapping.jvms.descriptor.method

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
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

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == 'V'
        }

        override fun read(reader: BufferedSource): VoidDescriptor {
            val value = reader.readUtf8CodePoint()
            if (value.checkedToChar() != 'V') {
                throw IllegalArgumentException("Invalid void type: ${value.toUnicode()}")
            }
            return VoidDescriptor(value.toChar())
        }
    }

    override fun toString() = value.toString()

}