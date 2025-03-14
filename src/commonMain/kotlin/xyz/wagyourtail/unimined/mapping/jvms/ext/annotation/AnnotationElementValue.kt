package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
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
value class AnnotationElementValue private constructor(val value: String) : Type {

    companion object: TypeCompanion<AnnotationElementValue> {
        private val innerTypes = listOf(
            Annotation,
            ArrayConstant,
            EnumConstant,
            ClassConstant,
            Constant
        )

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val type = innerTypes.first { it.shouldRead(reader.copy()) }.read(reader)
            append(type)
            if (type is Constant && type.isNull()) throw IllegalArgumentException("Null annotation element value")
        }

        override fun unchecked(value: String) = AnnotationElementValue(value)
    }

    fun isAnnotation() = value[0] == '@'

    fun isArrayConstant() = value[0] == '{'

    fun isEnumConstant() = StringCharReader(value).let {
        EnumConstant.shouldRead(it)
    }

    fun isClassConstant() = !isEnumConstant() && value[0] == 'L'

    fun isConstant() = value[0].isDigit() || value[0] == '"' || value[0] == 't' || value[0] == 'f'

    fun getAnnotation() = Annotation.unchecked(value)

    fun getArrayConstant() = ArrayConstant.unchecked(value)

    fun getEnumConstant() = EnumConstant.unchecked(value)

    fun getClassConstant() = ClassConstant.unchecked(value)

    fun getConstant() = Constant.unchecked(value)

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
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