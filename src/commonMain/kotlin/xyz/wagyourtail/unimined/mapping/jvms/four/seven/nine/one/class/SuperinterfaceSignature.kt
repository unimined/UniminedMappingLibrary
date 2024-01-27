package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * SuperinterfaceSignature:
 *   [ClassTypeSignature]
 */
@JvmInline
value class SuperinterfaceSignature private constructor(val value: ClassTypeSignature) {

    companion object: TypeCompanion<SuperinterfaceSignature> {

        override fun shouldRead(reader: CharReader) = ClassTypeSignature.shouldRead(reader)

        override fun read(reader: CharReader) = try {
            SuperinterfaceSignature(ClassTypeSignature.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid superinterface signature", e)
        }

        override fun unchecked(value: String) = SuperinterfaceSignature(ClassTypeSignature.unchecked(value))

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}




