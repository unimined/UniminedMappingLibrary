package xyz.wagyourtail.unimined.mapping.test.jvms

import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.DocSegment
import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc
import kotlin.test.Test
import kotlin.test.assertEquals

class JavadocGrammarTest {

    private fun refs(text: String) = Javadoc.unchecked(text).segments
        .filterIsInstance<DocSegment.TagSegment>()

    @Test
    fun parse() {
        // {/link} inline
        assertEquals(1, refs("a {@link x.Target} doc").size)
        assertEquals("x.Target", refs("a {@link x.Target label}").first().ref.toString())
        assertEquals("x.Target#m", refs("{@link x.Target#m}").first().ref.toString())
        assertEquals("x.Target#m(int, x.Iface)", refs("{@link x.Target#m(int, x.Iface)}").first().ref.toString())
        // block tags
        println("SEE0: " + refs("@see x.Target").map { it.ref })
        println("SEE1: " + refs("@see x.Target#m").map { it.ref })
        println("SEE2: " + refs("doc @see x.Target#m(int) label").map { it.ref })
        println("THROWS: " + refs("@throws x.Err").map { it.ref })
        assertEquals("x.Target", refs("@see x.Target").first().ref.toString())
        assertEquals("x.Target#m", refs("@see x.Target#m").first().ref.toString())
        assertEquals("x.Err", refs("@throws x.Err").first().ref.toString())
        // kdoc
        assertEquals("x.Target", refs("see [x.Target] doc").first().ref.toString())
        assertEquals("x.Target#m", refs("[x.Target#m]").first().ref.toString())
    }
}
