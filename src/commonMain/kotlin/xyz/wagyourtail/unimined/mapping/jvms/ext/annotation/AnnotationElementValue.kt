package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
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

        override fun shouldRead(reader: CharReader): Boolean {
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader) = try {
            AnnotationElementValue(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid annotation element value", e)
        }

        override fun unchecked(value: String) = AnnotationElementValue(value)
    }

    fun isAnnotation() = value[0] == '@'

    fun isArrayConstant() = value[0] == '{'

    fun isEnumConstant() = CharReader(value).use {
        EnumConstant.shouldRead(it)
    }

    fun isClassConstant() = !isEnumConstant() && value[0] == 'L'

    fun isConstant() = value[0].isDigit() || value[0] == '"' || value[0] == 't' || value[0] == 'f'

    fun getAnnotation() = Annotation.unchecked(value)

    fun getArrayConstant() = ArrayConstant.unchecked(value)

    fun getEnumConstant() = EnumConstant.unchecked(value)

    fun getClassConstant() = ClassConstant.unchecked(value)

    fun getConstant() = Constant.unchecked(value)

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