package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * FieldType:
 *   [BaseType]
 *   [ObjectType]
 *   [ArrayType]
 */
@JvmInline
value class FieldType private constructor(val value: String) : Type {

    companion object: TypeCompanion<FieldType> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(BaseType, ObjectType, ArrayType)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
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