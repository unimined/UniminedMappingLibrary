package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
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

        override fun shouldRead(reader: CharReader) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader) = try {
            FieldType(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field type", e)
        }

        override fun unchecked(value: String) = FieldType(value)
    }

    fun isBaseType() = value.length == 1

    fun isObjectType() = value.startsWith('L')

    fun isArrayType() = value.startsWith('[')

    fun getBaseType() = BaseType.unchecked(value)

    fun getObjectType() = ObjectType.unchecked(value)

    fun getArrayType() = ArrayType.unchecked(value)

    fun getWidth() = when {
        isBaseType() -> getBaseType().getWidth()
        isObjectType() -> 1
        else -> 1
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isBaseType()) {
                getBaseType().accept(visitor)
            } else if (isObjectType()) {
                getObjectType().accept(visitor)
            } else {
                getArrayType().accept(visitor)
            }
        }
    }

    override fun toString() = value

}