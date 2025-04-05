package xyz.wagyourtail.unimined.mapping.test.jvms.ext.constant

import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.test.jvms.buildStringAcceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ConstantTests {

    @Test
    fun basics() {
        Constant.read("null")
        Constant.read("false")
        Constant.read("100")
        Constant.read("10e+24")
        Constant.read("100.23e-46")
        Constant.read(".23e-46")
        Constant.read("0xFFFF10")
        Constant.read("-100")
        Constant.read("-InfinityF")
        Constant.read("-.2f")
    }

    @Test
    fun illegal() {
        assertFails {
            Constant.read("09")
        }

        assertFails {
            Constant.read("0xg")
        }

        assertFails {
            Constant.read("0b")
        }

        assertFails {
            Constant.read("0b2")
        }

        assertFails {
            Constant.read("0x")
        }

        assertFails {
            Constant.read("0x1h")
        }
    }

    @Test
    fun testVisitor() {
        val c = Constant.read("null")
        assertEquals("null", buildStringAcceptor(c))

        val c1 = Constant.read("true")
        assertEquals("true", buildStringAcceptor(c1))

        val c2 = Constant.read("10e+24")
        assertEquals("10e+24", buildStringAcceptor(c2))

        val c3 = Constant.read("-NaND")
        assertEquals("-NaND", buildStringAcceptor(c3))

        val c4 = Constant.read("0.10e+24f")
        assertEquals("0.10e+24f", buildStringAcceptor(c4))

        val c5 = Constant.read("0b1010")
        assertEquals("0b1010", buildStringAcceptor(c5))

        val c6 = Constant.read("0x1010L")
        assertEquals("0x1010L", buildStringAcceptor(c6))

        val c7 = Constant.read("0123l")
        assertEquals("0123l", buildStringAcceptor(c7))

        val c8 = Constant.read("0")
        assertEquals("0", buildStringAcceptor(c8))

        val c9 = Constant.read("1")
        assertEquals("1", buildStringAcceptor(c9))
    }

    @Test
    fun testValue() {
        val c = Constant.read("null")
        assertTrue(c.isNull())

        val c1 = Constant.read("true")
        assertTrue(c1.getBoolean()!!.value)

        val c2 = Constant.read("10e+24")
        assertEquals(10e24, c2.getNumber()!!.asNumber())

        val c3 = Constant.read("NaND")
        val c3n = c3.getNumber()!!.asNumber()
        assertTrue(c3n is Double && c3n.isNaN())

        val c4 = Constant.read("0.10e+24f")
        assertEquals(0.10e24f, c4.getNumber()!!.asNumber())

        val c5 = Constant.read("0b1010")
        assertEquals(0b1010, c5.getNumber()!!.asNumber())

        val c6 = Constant.read("0x1010L")
        assertEquals(0x1010L, c6.getNumber()!!.asNumber())

        val c7 = Constant.read("0123l")
        assertEquals("0123".toInt(8).toLong(), c7.getNumber()!!.asNumber())

        val c8 = Constant.read("0")
        assertEquals(0, c8.getNumber()!!.asNumber())

        val c9 = Constant.read("1")
        assertEquals(1, c9.getNumber()!!.asNumber())

        val c10 = Constant.read("-110")
        assertEquals(-110, c10.getNumber()!!.asNumber())

        val c11 = Constant.read("-0x109L")
        assertEquals(-0x109L, c11.getNumber()!!.asNumber())
    }

}