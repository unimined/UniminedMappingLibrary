package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * BaseType:
 *   (one of)
 *   B C D F I J S Z
 */
@JvmInline
value class BaseType private constructor(val value: Char) {

    companion object: TypeCompanion<BaseType> {
        private val types = setOf('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z')

        override fun shouldRead(reader: BufferedSource): Boolean {
            val value = reader.readUtf8CodePoint()
            return value.checkedToChar() in types
        }

        override fun read(reader: BufferedSource): BaseType {
            val value = reader.readUtf8CodePoint()
            if (value.checkedToChar() !in types) {
                throw IllegalArgumentException("Invalid base type")
            }
            return BaseType(value.toChar())
        }
    }

    override fun toString() = value.toString()

}