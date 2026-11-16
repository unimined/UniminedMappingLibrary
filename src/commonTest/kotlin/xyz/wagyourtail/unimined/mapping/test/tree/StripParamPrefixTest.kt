package xyz.wagyourtail.unimined.mapping.test.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.stripParamPrefix
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [stripParamPrefix] — loom `ParchmentPrefixStripingMappingVisitor`
 * parity: `<prefix><Uppercase>rest` → `<lowercase>rest`, optionally
 * restricted to a namespace set.
 */
class StripParamPrefixTest {

    private val official = Namespace("official")
    private val named = Namespace("named")

    @Test
    fun `strips marker prefix across all namespaces by default`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                method("m;(I)V", "m") {
                    parameter(0, null, "pFoo", "pBar")
                }
            }
        }
        tree.stripParamPrefix()
        val p = tree.getClass(official, InternalName.unchecked("a/A"))!!
            .methods.single().parameters.single()
        assertEquals("foo", p.names[official]?.value)
        assertEquals("bar", p.names[named]?.value)
    }

    @Test
    fun `namespace filter limits the strip`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                method("m;(I)V", "m") {
                    parameter(0, null, "pFoo", "pBar")
                }
            }
        }
        tree.stripParamPrefix(namespaces = setOf(named))
        val p = tree.getClass(official, InternalName.unchecked("a/A"))!!
            .methods.single().parameters.single()
        assertEquals("pFoo", p.names[official]?.value)
        assertEquals("bar", p.names[named]?.value)
    }

    @Test
    fun `non-marker names stay`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                method("m;(I)V", "m") {
                    // second char not uppercase — no marker
                    parameter(0, null, "plower", "arg")
                    // single char — too short to carry the marker
                    parameter(1, null, "p", "x")
                    // prefix mid-name doesn't count
                    parameter(2, null, "apFoo", "apBar")
                }
            }
        }
        tree.stripParamPrefix()
        val params = tree.getClass(official, InternalName.unchecked("a/A"))!!
            .methods.single().parameters.toList()
        assertEquals("plower", params[0].names[official]?.value)
        assertEquals("arg", params[0].names[named]?.value)
        assertEquals("p", params[1].names[official]?.value)
        assertEquals("x", params[1].names[named]?.value)
        assertEquals("apFoo", params[2].names[official]?.value)
        assertEquals("apBar", params[2].names[named]?.value)
    }

    @Test
    fun `custom prefix strips`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                method("m;(I)V", "m") {
                    parameter(0, null, "xFoo", "pBar")
                }
            }
        }
        tree.stripParamPrefix(prefix = 'x')
        val p = tree.getClass(official, InternalName.unchecked("a/A"))!!
            .methods.single().parameters.single()
        assertEquals("foo", p.names[official]?.value)
        // `p` is no longer the marker
        assertEquals("pBar", p.names[named]?.value)
    }

    @Test
    fun `wildcard params strip too`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                wildcard(
                    xyz.wagyourtail.unimined.mapping.element.WildcardType.METHOD,
                    "(I)V"
                )
            }
        }
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        (cls.wildcards.single() as xyz.wagyourtail.unimined.mapping.element.mapping.MutableWildcardMapping).parameter(
            0, null,
            mapOf(named to xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("pArg"))
        )
        tree.stripParamPrefix()
        assertEquals("arg", cls.wildcards.single().parameters.single().names[named]?.value)
    }

}
