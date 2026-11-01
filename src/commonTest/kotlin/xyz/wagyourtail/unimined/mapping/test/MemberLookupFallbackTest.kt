package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.mappingTree
import xyz.wagyourtail.unimined.mapping.remapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Desc-less member rows are a last-resort fallback: they may only match a
 * desc'd lookup when no desc-bearing row exists for the name at all
 * (ornithe b1.7.3 `serverOfficial→calamus` collapse — server rows carry
 * names only; the descriptors live on the client cells). A lone desc-less
 * row must never claim every unverifiable query on a shared name.
 */
class MemberLookupFallbackTest {

    private val server = Namespace("server")
    private val calamus = Namespace("calamus")

    private val tree = mappingTree("client", "server", "calamus") {
        clazz("eq", "om", "C_eq")
        clazz("fd", "dj", "C_world") {
            // server cells name-only; descs live on the client cell and are
            // derived for `server` via getDescriptor's class-ref remap
            method("x;(Leq;)V", "a", "m_1")
            method("x;(Leq;I)V", "a", "m_2")
            // client-only class — desc can't remap into server space
            method("x;(Lcl;)V", "c", "m_4")
            // desc-less row sharing the same server name — blocked: "a" has
            // desc-bearing rows, so m_5 can never claim a desc'd lookup
            method("d", "a", "m_5")
            // a second desc-less "a" row would be merged at resolve — the
            // ambiguous-desc-less guard is defensive only
            // desc-less-only name — the fallback still applies here
            method("d2", "g", "m_6")
            field("w;Leq;", "f", "f_1")
            field("w;I", "f", "f_2")
            field("h", "f", "f_3")
        }
    }

    private val cls get() = tree.getClass(server, InternalName.unchecked("dj"))!!
    private fun md(s: String) = MethodDescriptor.unchecked(s)
    private fun uqn(s: String) = UnqualifiedName.unchecked(s)

    @Test
    fun `server desc is derived from another namespace`() {
        with(tree) {
            // Leq → Lom class-ref remap: the name-only server row still
            // identifies a specific overload by desc
            assertEquals("m_1", cls.getMethod(server, uqn("a"), md("(Lom;)V"))!!.names[calamus]!!.value)
            assertEquals("m_2", cls.getMethod(server, uqn("a"), md("(Lom;I)V"))!!.names[calamus]!!.value)
        }
    }

    @Test
    fun `desc-bearing match excludes desc-less rows`() {
        with(tree) {
            // "a;(Leq;)V" verifies — the desc-less "a" row must NOT be
            // returned alongside it
            assertEquals(
                listOf("m_1"),
                cls.getMethods(server, uqn("a"), md("(Lom;)V")).map { it.names[calamus]!!.value }
            )
        }
    }

    @Test
    fun `unresolvable desc keeps source name`() {
        with(tree) {
            // no row's server-space desc is (LZZ;)V — desc-bearing rows fail
            // verification, no desc-less row exists → no match at all
            assertNull(cls.getMethod(server, uqn("b"), md("(LZZ;)V")))
            // client-only class Lcl can't remap into server space → no match
            assertNull(cls.getMethod(server, uqn("c"), md("(Lom;)V")))
        }
    }

    @Test
    fun `desc-less row can't claim a name with desc-bearing rows`() {
        with(tree) {
            // nothing verifies (LZZ;)V on "a", and desc-bearing "a" rows
            // exist → the desc-less "a" row must NOT vacuum the query
            assertNull(cls.getMethod(server, uqn("a"), md("(LZZ;)V")))
            // but a desc-less-only name still resolves via fallback
            assertEquals("m_6", cls.getMethod(server, uqn("g"), md("(LZZ;)V"))!!.names[calamus]!!.value)
        }
    }

    @Test
    fun `field fallback mirrors method fallback`() {
        with(tree) {
            assertEquals("f_1", cls.getField(server, uqn("f"), FieldDescriptor.unchecked("Lom;"))!!.names[calamus]!!.value)
            assertEquals("f_2", cls.getField(server, uqn("f"), FieldDescriptor.unchecked("I"))!!.names[calamus]!!.value)
            // Lcl; can't be verified and desc-bearing "f" rows exist →
            // desc-less "f" row is blocked (no vacuum)
            assertNull(cls.getField(server, uqn("f"), FieldDescriptor.unchecked("Lcl;")))
        }
    }

    @Test
    fun `remapper preserves source name when desc can't be verified`() {
        val r = tree.remapper(server, calamus)
        val dj = InternalName.unchecked("dj")
        assertEquals("m_1", r.mapMethod(dj, uqn("a"), md("(Lom;)V")).value)
        // desc-bearing "a" rows exist → desc-less fallback blocked → kept
        assertEquals("a", r.mapMethod(dj, uqn("a"), md("(LZZ;)V")).value)
        assertEquals("m_6", r.mapMethod(dj, uqn("g"), md("(LZZ;)V")).value)
        assertEquals("c", r.mapMethod(dj, uqn("c"), md("(Lom;)V")).value)
        assertEquals("f_2", r.mapField(dj, uqn("f"), FieldDescriptor.unchecked("I")).value)
    }

}
