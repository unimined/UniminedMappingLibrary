package xyz.wagyourtail.unimined.mapping.test.jvms.ext.expression

import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.ConstantExpressionEvaluator
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConstantExpressionEvaluatorTest {

    private fun eval(e: String) = ConstantExpressionEvaluator.eval(Expression.unchecked(e))

    @Test
    fun foldsLiterals() {
        assertEquals(42, eval("20 + 22")?.value)
        assertEquals(18, eval("1 << 4 | 2")?.value)
        assertEquals(7, eval("(1 | 2) + 4")?.value)
        assertEquals(-5, eval("-5")?.value)
        assertEquals(44, eval("<byte>300")?.value)
        assertEquals(2L, eval("1 + <long>1")?.value)
        assertEquals("hi", eval("\"hi\"")?.value)
    }

    @Test
    fun foldsFieldRefsWithResolver() {
        val expr = Expression.unchecked("La;f_1;I | La;f_2;I")
        // unresolvable without a field index → can't prove a value
        assertNull(ConstantExpressionEvaluator.eval(expr))
        val fields = mapOf("a.f_1" to (1 to "I"), "a.f_2" to (2 to "I"))
        val r = ConstantExpressionEvaluator.eval(expr,
            resolveField = { o, n -> fields["${o.value}.${n.value}"] })
        assertEquals(3, r?.value)
        assertEquals(ConstantExpressionEvaluator.Sort.INT, r?.sort)
    }

    @Test
    fun checksDeclaredValues() {
        assertEquals(true, ConstantExpressionEvaluator.check(
            Constant.unchecked("42"), Expression.unchecked("20 + 22")))
        assertEquals(false, ConstantExpressionEvaluator.check(
            Constant.unchecked("42"), Expression.unchecked("20 + 23")))
        // unprovable — no field index — is not a rejection
        assertNull(ConstantExpressionEvaluator.check(
            Constant.unchecked("3"), Expression.unchecked("La;f_1;I | 2")))
        // non-literal declared value — nothing to check against
        assertNull(ConstantExpressionEvaluator.check(
            Constant.unchecked("La;f_1;I"), Expression.unchecked("La;f_1;I")))
    }

    @Test
    fun comparesValuesAcrossSorts() {
        assertEquals(true, ConstantExpressionEvaluator.valuesEqual(3, 3L))
        assertEquals(true, ConstantExpressionEvaluator.valuesEqual(true, 1))
        assertEquals(true, ConstantExpressionEvaluator.valuesEqual(1, true))
        assertEquals(false, ConstantExpressionEvaluator.valuesEqual(3, 3.5))
        assertEquals(true, ConstantExpressionEvaluator.valuesEqual("x", "x"))
        assertEquals(true, ConstantExpressionEvaluator.check(
            Constant.unchecked("42"), Expression.unchecked("<long>42")))
    }

}
