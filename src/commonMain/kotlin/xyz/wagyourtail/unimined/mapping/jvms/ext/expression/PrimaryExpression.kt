package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import kotlin.jvm.JvmInline

/**
 * PrimaryExpression:
 *   [ParenExpression]
 *   [FieldExpression]
 *   [Constant]
 */
@JvmInline
value class PrimaryExpression(val value: String) : Type {

    companion object : TypeCompanion<PrimaryExpression> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(ParenExpression, FieldExpression, Constant)

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String): PrimaryExpression {
            return PrimaryExpression(value)
        }

    }

    fun isParen() = value.startsWith('(')

    fun isField() = value.startsWith("L") || value.startsWith("this.")

    fun isConstant() = !isParen() && !isField()

    fun getParen() = if (isParen()) ParenExpression.unchecked(value) else null

    fun getField() = if (isField()) FieldExpression.unchecked(value) else null

    fun getConstant() = if (isConstant()) Constant.unchecked(value) else null

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            getParen()?.accept(visitor)
            getField()?.accept(visitor)
            getConstant()?.accept(visitor)
        }
    }

    override fun toString() = value

}