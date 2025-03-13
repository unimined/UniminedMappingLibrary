package xyz.wagyourtail.unimined.mapping.test.jvms.ext.expression

import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
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
        val (left, op, right) = e.value.getParts().second.getParts().second.getParts().second.getParts().third.getParts()
        assertEquals("100", left.toString())
        assertEquals("+", op)
        assertEquals("5", right.toString())

        val e1 = Expression.read("100 + 5 * 20 - 10 / 2")
        val (left1, op1, right1) = e1.value.getParts().second.getParts().second.getParts().second.getParts().third.getParts()
        assertEquals("100 + 5 * 20", left1.toString())
        assertEquals("-", op1)
        assertEquals("10 / 2", right1.toString())
        val (left2, op2, right2) = left1!!.getParts()
        assertEquals("100", left2.toString())
        assertEquals("+", op2)
        assertEquals("5 * 20", right2.toString())
        val (left3, op3, right3) = right2.getParts()
        assertEquals("5", left3.toString())
        assertEquals("*", op3)
        assertEquals("20", right3.toString())

        val e2 = Expression.read("1 << 5 * 2")
        val (left4, op4, right4) = e2.value.getParts().second.getParts().second.getParts().second.getParts()
        assertEquals("1", left4.toString())
        assertEquals("<<", op4)
        assertEquals("5 * 2", right4.toString())
        val (left5, op5, right5) = right4.getParts().third.getParts()
        assertEquals("5", left5.toString())
        assertEquals("*", op5)
        assertEquals("2", right5.toString())
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

        val e2 = Expression.read("1.4e-4 << 5 * 2 + 0x10 + 046")
        assertEquals("1.4e-4 << 5 * 2 + 0x10 + 046", buildStringAcceptor(e2))
    }

}