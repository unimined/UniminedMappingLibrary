package xyz.wagyourtail.unimined.mapping.jvms.descriptor.field

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * FieldType:
 *   [BaseType]
 *   [ObjectType]
 *   [ArrayType]
 */
@JvmInline
value class FieldType private constructor(val value: String) {

    companion object: TypeCompanion<FieldType> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(BaseType, ObjectType, ArrayType)

        override fun shouldRead(reader: BufferedSource) =
            innerTypes.firstOrNull { it.shouldRead(reader.peek()) }?.shouldRead(reader) == true

        override fun read(reader: BufferedSource) = try {
            FieldType(innerTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field type", e)
        }
    }

    fun isBaseType() = value.length == 1

    fun isObjectType() = value.startsWith('L')

    fun isArrayType() = value.startsWith('[')

    fun getBaseType() = BaseType.read(value)

    fun getObjectType() = ObjectType.read(value)

    fun getArrayType() = ArrayType.read(value)

    override fun toString() = value

}