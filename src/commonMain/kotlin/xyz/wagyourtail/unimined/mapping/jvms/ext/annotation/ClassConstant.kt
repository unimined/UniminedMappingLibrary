package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * ClassConstant:
 *    [ObjectType]
 */
@JvmInline
value class ClassConstant private constructor(val value: ObjectType) {

    companion object: TypeCompanion<ClassConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return ObjectType.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>) = try {
            ClassConstant(ObjectType.read(reader))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid class constant", e)
        }

        override fun unchecked(value: String) = ClassConstant(ObjectType.unchecked(value))
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}