package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.envView
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class EnvSelectorTest {

    private val off = Namespace("official")
    private val calamus = Namespace("calamus")

    @Test
    fun `forEnv and splitEnv round-trip`() {
        val q = off.forEnv("client")
        assertEquals("official:client", q.name)
        val (base, env) = q.splitEnv()
        assertSame(off, base)
        assertEquals("client", env)
        // unqualified namespaces carry no env
        assertEquals(off to null, off.splitEnv())
        assertSame(off, off.forEnv(null))
    }

    @Test
    fun `env view collapses sided rows onto joined`() {
        val tree = mappingTree("official", "official:client", "official:server", "calamus") {
            // joined row — obf name valid on both sides
            clazz("shared/A", null, null, "net/mc/A")
            // client-sided row — only in the client obf domain
            clazz(null, "aa/B", null, "net/mc/client/B")
            // server-sided row
            clazz(null, null, "bb/C", "net/mc/server/C")
        }

        // envView(client) — official = official ∪ official:client
        val client = tree.envView("client")
        assertEquals(
            "net/mc/A",
            client.getClass(off, InternalName.unchecked("shared/A"))!!
                .names[calamus]!!.value
        )
        assertEquals(
            "net/mc/client/B",
            client.getClass(off, InternalName.unchecked("aa/B"))!!
                .names[calamus]!!.value
        )
        // a client view does not resolve server-sided rows
        assertNull(client.getClass(off, InternalName.unchecked("bb/C")))

        // envView(server) — official = official ∪ official:server
        val server = tree.envView("server")
        assertEquals(
            "net/mc/A",
            server.getClass(off, InternalName.unchecked("shared/A"))!!
                .names[calamus]!!.value
        )
        assertEquals(
            "net/mc/server/C",
            server.getClass(off, InternalName.unchecked("bb/C"))!!
                .names[calamus]!!.value
        )
        assertNull(server.getClass(off, InternalName.unchecked("aa/B")))

        // joined view = the unqualified column only
        val joined = tree.envView(null)
        assertNull(joined.getClass(off, InternalName.unchecked("aa/B")))
        assertNull(joined.getClass(off, InternalName.unchecked("bb/C")))
        assertEquals(
            "net/mc/A",
            joined.getClass(off, InternalName.unchecked("shared/A"))!!
                .names[calamus]!!.value
        )
    }

    @Test
    fun `map on env view resolves the collapsed column`() {
        val tree = mappingTree("official", "official:client", "calamus") {
            clazz("shared/A", null, "net/mc/A")
            clazz(null, "aa/B", "net/mc/client/B")
        }

        val client = tree.envView("client")
        // joined row maps through the env view
        assertEquals(
            "net/mc/A",
            client.map(off, calamus, InternalName.unchecked("shared/A")).value
        )
        // client-sided row maps through the env view
        assertEquals(
            "net/mc/client/B",
            client.map(off, calamus, InternalName.unchecked("aa/B")).value
        )
        // the source tree's plain joined view can't see the sided row
        assertEquals(
            "aa/B",
            tree.map(off, calamus, InternalName.unchecked("aa/B")).value
        )
    }

}
