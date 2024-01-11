package xyz.wagyourtail.unimined.mapping.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * AnnotationElementValue:
 *   [Annotation]
 *   [ArrayConstant]
 *   [EnumConstant]
 *   [ClassConstant]
 *   [Constant]
 */

@JvmInline
value class AnnotationElementValue private constructor(val value: String) {

    companion object: TypeCompanion<AnnotationElementValue> {
        private val innerTypes = listOf(
            Annotation,
            ArrayConstant,
            EnumConstant,
            ClassConstant,
            Constant
        )

        override fun shouldRead(reader: BufferedSource): Boolean {
            return innerTypes.first { it.shouldRead(reader.peek()) }.shouldRead(reader)
        }

        override fun read(reader: BufferedSource) = try {
            AnnotationElementValue(innerTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element value", e)
        }
    }

    fun isAnnotation() = value[0] == '@'

    fun isArrayConstant() = value[0] == '{'

    fun isEnumConstant() = Buffer().use {
        it.writeUtf8(value)
        EnumConstant.shouldRead(it)
    }

    fun isClassConstant() = !isEnumConstant() && value[0] == 'L'

    fun isConstant() = value[0].isDigit() || value[0] == '"' || value[0] == 't' || value[0] == 'f'

    fun getAnnotation() = Annotation.read(value)

    fun getArrayConstant() = ArrayConstant.read(value)

    fun getEnumConstant() = EnumConstant.read(value)

    fun getClassConstant() = ClassConstant.read(value)

    fun getConstant() = Constant.read(value)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            when {
                isAnnotation() -> getAnnotation().accept(visitor)
                isArrayConstant() -> getArrayConstant().accept(visitor)
                isEnumConstant() -> getEnumConstant().accept(visitor)
                isClassConstant() -> getClassConstant().accept(visitor)
                isConstant() -> getConstant().accept(visitor)
            }
        }
    }

    override fun toString() = value

}