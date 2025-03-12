package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ObjectType:
 *   L [InternalName] ;
 */
@JvmInline
value class ObjectType private constructor(val value: String) : Type {

    companion object: TypeCompanion<ObjectType> {

        override fun shouldRead(reader: CharReader<*>) = reader.take() == 'L'

        override fun read(reader: CharReader<*>): ObjectType {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid object type")
            }
            return ObjectType(buildString {
                append('L')
                append(InternalName.read(reader))
                val end = reader.take()
                if (end != ';') {
                    throw IllegalArgumentException("Invalid object type, expected ;, found $end")
                }
                append(';')
            })
        }

        operator fun invoke(internalName: InternalName) = ObjectType("L$internalName;")

        override fun unchecked(value: String) = ObjectType(value)
    }

    fun getInternalName() = InternalName.unchecked(value.substring(1, value.length - 1))

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("L")
            getInternalName().accept(visitor)
            visitor(";")
        }
    }

    override fun toString() = value

}