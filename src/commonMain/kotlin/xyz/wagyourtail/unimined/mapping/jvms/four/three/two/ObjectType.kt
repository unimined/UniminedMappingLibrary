package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * ObjectType:
 *   L ClassName ;
 */
@JvmInline
value class ObjectType private constructor(val value: String) {

    companion object: TypeCompanion<ObjectType> {

        override fun shouldRead(reader: BufferedSource) = reader.readUtf8CodePoint().checkedToChar() == 'L'

        override fun read(reader: BufferedSource): ObjectType {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid object type")
            }
            return ObjectType(buildString {
                append('L')
                while (true) {
                    val value = reader.takeUTF8Until { it.checkedToChar() in JVMS.unqualifiedNameIllagalChars }
                    val end = reader.readUtf8CodePoint().checkedToChar()
                    append(value)
                    append(end)
                    when (end) {
                        ';' -> {
                            return@buildString
                        }

                        '/' -> {}
                        else -> throw IllegalArgumentException("Invalid object type, found illegal character: $end")
                    }
                }
            })
        }
    }

    fun getInternalName() = value.substring(1, value.length - 1)

    override fun toString() = value

}