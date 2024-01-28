package xyz.wagyourtail.unimined.mapping.test.utils

import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.util.translateEscapes
import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {

    @Test
    fun testTranslateEscapes() {

        assertEquals("test", "test".translateEscapes())

        // octal
        assertEquals(" 2", "\\402".translateEscapes())
        assertEquals("~", "\\176".translateEscapes())
        assertEquals("\n", "\\12".translateEscapes())
        assertEquals("\ntest", "\\12test".translateEscapes())
        assertEquals("\u0000", "\\0".translateEscapes())

        // unicode
        assertEquals("\u0000", "\\u0000".translateEscapes())
        assertEquals("\u0001", "\\u0001".translateEscapes())

        // other escapes
        assertEquals("\\", "\\\\".translateEscapes())
        assertEquals("\"", "\\\"".translateEscapes())
        assertEquals("'", "\\'".translateEscapes())
        assertEquals("\b", "\\b".translateEscapes())
        assertEquals("\u000C", "\\f".translateEscapes())
        assertEquals("\n", "\\n".translateEscapes())
        assertEquals("\r", "\\r".translateEscapes())
        assertEquals(" ", "\\s".translateEscapes())
        assertEquals("\t", "\\t".translateEscapes())

    }

    @Test
    fun testEscape() {

        assertEquals("test", "test".escape())
        assertEquals("\\\"", "\"".escape())
        assertEquals("\\'", "'".escape())
        assertEquals("\\\\", "\\".escape())
        assertEquals("\\b", "\b".escape())
        assertEquals("\\f", "\u000C".escape())
        assertEquals("\\n", "\n".escape())
        assertEquals("\\r", "\r".escape())
        assertEquals("\\s", " ".escape(spaces = true))
        assertEquals(" ", " ".escape(spaces = false))
        assertEquals("\\t", "\t".escape())
        assertEquals("\\u0000", "\u0000".escape(unicode = true))
        assertEquals("\u0000", "\u0000".escape(unicode = false))

    }

}