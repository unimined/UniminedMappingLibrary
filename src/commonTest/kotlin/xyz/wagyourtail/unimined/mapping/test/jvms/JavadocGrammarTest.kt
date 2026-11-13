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

    /**
     * parse→render must round-trip verbatim: readTag used to consume a
     * failed candidate's body before rejecting it, collapsing `[/x]` to
     * `[` and truncating at `{@code byte[)}`. Serialized bundle/cache
     * round-trips then corrupted comments a generation at a time.
     */
    @Test
    fun lossless() {
        val corpus = listOf(
            // bracketed literals inside {@code} are NOT links
            "{@code [/MOTD]} // marker", "{@code [/AD]}",
            "{@code byte[} is an array", "{@code byte[)}",
            "[MOTD]A Player's Server[[AD]192.168.0.33[/AD][/MOTD]",
            "{@code 192.146.2.1:23132}", "[not a ref]",
            // valid links still parse — and re-emit identically
            "{@link x.Target label}", "[x.Target#m]",
            "@see x.Target#m(int) label", "plain text",
        )
        for (text in corpus) {
            assertEquals(text, Javadoc.unchecked(text).toString(), "first render for: $text")
            // and the rendered form re-parses to the same segments —
            // serialization round-trips must be byte-stable
            assertEquals(text, Javadoc.unchecked(Javadoc.unchecked(text).toString()).toString(),
                "second render for: $text")
        }
        // valid refs still map through
        assertEquals(1, refs("{@code byte[} and [x.Target]").size)
        assertEquals("x.Target", refs("a [/x] b [x.Target] c").first().ref.toString())
    }
}
