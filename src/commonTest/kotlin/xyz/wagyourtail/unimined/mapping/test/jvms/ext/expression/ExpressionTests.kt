package xyz.wagyourtail.unimined.mapping.test.jvms.ext.expression

import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.NumberConstant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.AdditiveExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.MultiplicativeExpression
import xyz.wagyourtail.unimined.mapping.test.jvms.buildStringAcceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ExpressionTests {

    @Test
    fun basics() {
        Expression.read("100 * 5")
        Expression.read("100 + 5")
        Expression.read("100 + 5 * 20 - 10 / 2")
        Expression.read("1 << 5 * 2")
        Expression.read("Ljava/lang/Math;PI; * 2")
        Expression.read("Ljava/lang/Math;PI;D * 2")
        Expression.read("Ljava/lang/Math;this.PI;D * 2")
        Expression.read("this.PI;D * 2")
        Expression.read("<String>(<float>1.0 * -<double>~2)")
    }

    @Test
    fun testStructure() {
        val e = Expression.read("100 + 5")
        val (expr, right) = e.value.getParts().second.getParts().second.getParts().second.getParts().second.getParts()
        val (left, op) = expr.first()
        assertEquals("100", left.toString())
        assertEquals("+", op)
        assertEquals("5", right.toString())

        val e1 = Expression.read("100 + 5 * 20 - 10 / 2")
        val (expr1, right1) = e1.value.getParts().second.getParts().second.getParts().second.getParts().second.getParts()
        assertEquals(listOf(
            Pair(MultiplicativeExpression.unchecked("100"), "+"),
            Pair(MultiplicativeExpression.unchecked("5 * 20"), "-"),
        ), expr1)
        assertEquals("10 / 2", right1.toString())

        val e2 = Expression.read("1 << 5 * 2")
        val (expr2, right2) = e2.value.getParts().second.getParts().second.getParts().second.getParts()
        assertEquals(listOf(
            Pair(AdditiveExpression.unchecked("1"), "<<")
        ), expr2)
        assertEquals("5 * 2", right2.toString())
    }

    @Test
    fun illegal() {
        assertFails {
            Expression.read("100 + 5 +")
        }

        assertFails {
            Expression.read("100 + 5 *")
        }

        assertFails {
            Expression.read("+ 5")
        }

        assertFails {
            Expression.read("PI;")
        }

        assertFails {
            Expression.read("LI;")
        }

    }

    @Test
    fun testVisitor() {
        val e = Expression.read("100e+10 + 5")
        assertEquals("100e+10 + 5", buildStringAcceptor(e))

        val e1 = Expression.read("100.0 + 5 * 20 - 10 / 2 + 0.1e4")
        assertEquals("100.0 + 5 * 20 - 10 / 2 + 0.1e4", buildStringAcceptor(e1))

        val e2 = Expression.read("1.4e-4 << 5 * -2 + 0x10 + 046")
        assertEquals("1.4e-4 << 5 * -2 + 0x10 + 046", buildStringAcceptor(e2))

        val e3 = Expression.read("Ljava/lang/Math;P+I;D * 2")
        assertEquals("Ljava/lang/Math;P+I;D * 2", buildStringAcceptor(e3))
    }

}