/**
 * This file implements JVMS 4.3.3
 * https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3.3
 */
package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * VoidDescriptor:
 *   V
 */
@JvmInline
value class VoidDescriptor private constructor(val value: Char) : Type {

    companion object: TypeCompanion<VoidDescriptor> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'V'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('V'))
        }

        override fun unchecked(value: String) = VoidDescriptor(value.toCharArray()[0])
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value.toString()

}