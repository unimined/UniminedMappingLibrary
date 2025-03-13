package xyz.wagyourtail.unimined.mapping.jvms.ext.constant

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationIdentifier.Companion.annotationIdentifierIllegalCharacters
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ArrayType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.BaseType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType.Companion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import kotlin.jvm.JvmInline

/**
 * Constant:
 *   [StringConstant]
 *   [BooleanConstant]
 *   [NumberConstant]
 *   null
 */
@JvmInline
value class Constant private constructor(val value: String) : Type {

    companion object: TypeCompanion<Constant> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(StringConstant, BooleanConstant, NumberConstant)

        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (reader.peek() == 'n') {
                reader.take()
                return true
            }
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>) = try {
            if (reader.peek() == 'n') {
                for (char in "null") {
                    reader.expect(char)
                }
                Constant("null")
            } else {
                Constant(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader).toString())
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid constant", e)
        }

        override fun unchecked(value: String) = Constant(value)
    }

    fun isNull() = value == "null"

    fun isString() = value.first() == '"'

    fun isBoolean() = value == "true" || value == "false"

    fun isNumber() = NumberConstant.shouldRead(StringCharReader(value))

    fun getString() = if (isString()) StringConstant.unchecked(value) else null

    fun getBoolean() = value.first() == 't'

    fun getNumber() = if (isNumber()) NumberConstant.unchecked(value) else null

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            when {
                isNull() -> visitor("null")
                isString() -> StringConstant.unchecked(value).accept(visitor)
                isBoolean() -> BooleanConstant.unchecked(value).accept(visitor)
                else -> NumberConstant.unchecked(value).accept(visitor)
            }
        }
    }

    override fun toString() = value

}