package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * ObjectType:
 *   L [InternalName] ;
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
                append(InternalName.read(reader))
                val end = reader.readUtf8CodePoint().checkedToChar()
                if (end != ';') {
                    throw IllegalArgumentException("Invalid object type, expected ;, found $end")
                }
                append(';')
            })
        }
    }

    fun getInternalName() = InternalName.read(value.substring(1, value.length - 1))

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("L", true)
            getInternalName().accept(visitor)
            visitor(";", true)
        }
    }

    override fun toString() = value

}