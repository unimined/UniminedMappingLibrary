package xyz.wagyourtail.unimined.mapping.test

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.envView
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repro for the merged-gen2 merge failure: `uk extends ow` (client)
 * and `mu extends jh` (server) must both resolve superName to the
 * shared intermediary row (ow/jh pair on one class). The merge saw
 * client=C_8191584 (gv) / server=C_4636105 (gs) — wrong rows.
 */
class Gen2MergeReproTest {

    // skip absent repro fixtures — the /tmp files only exist
    // on machines that produced them
    private fun content(): String? = java.io.File(
        "/tmp/gen2cal/mappings/mappings.tiny"
    ).takeIf { it.exists() }?.readText()

    private fun n(name: String) = InternalName.unchecked(name)

    @Test
    fun sidedOfficialNamesResolve() = runTest {
        val content = content() ?: return@runTest
        val im = Namespace("intermediary")
        val off = Namespace("official")
        // pipeline relabel: clientOfficial/serverOfficial -> env cols
        val tree = TinyV2Reader.read(
            content,
            nsMapping = mapOf(
                "clientOfficial" to "official:client",
                "serverOfficial" to "official:server",
            ),
        )
        println("namespaces: ${tree.namespaces.map { it.name }}")

        // pre-view: exact env columns
        println(
            "uk@official:client -> " +
                tree.map(Namespace("official:client"), im, n("uk"))
        )
        println(
            "ow@official:client -> " +
                tree.map(Namespace("official:client"), im, n("ow"))
        )
        println(
            "mu@official:server -> " +
                tree.map(Namespace("official:server"), im, n("mu"))
        )
        println(
            "jh@official:server -> " +
                tree.map(Namespace("official:server"), im, n("jh"))
        )

        // post-view: what the merge remap resolves
        val cv = tree.envView("client")
        val sv = tree.envView("server")
        // regression for the fillMissingNames env-col bug: same obf
        // name on different intermediary ids per side must stay
        // distinct (np: client MusInputStream vs server JdomAdapter)
        assertEquals(
            InternalName.unchecked("net/minecraft/unmapped/C_7439439"),
            sv.map(off, im, n("m"))
        )
        assertEquals(
            InternalName.unchecked("net/minecraft/unmapped/C_8075208"),
            cv.map(off, im, n("m"))
        )
        assertEquals(
            InternalName.unchecked("net/minecraft/unmapped/C_6368853"),
            sv.map(off, im, n("np"))
        )
        assertEquals(
            InternalName.unchecked("net/minecraft/unmapped/C_8761487"),
            cv.map(off, im, n("np"))
        )
        val ukC = cv.map(off, im, n("uk"))
        val owC = cv.map(off, im, n("ow"))
        val muS = sv.map(off, im, n("mu"))
        val jhS = sv.map(off, im, n("jh"))
        println("uk@official[CLIENT] -> $ukC")
        println("ow@official[CLIENT] -> $owC")
        println("mu@official[SERVER] -> $muS")
        println("jh@official[SERVER] -> $jhS")
        // both sides' superName must land on the same shared row
        assertEquals(owC, jhS, "superclass ids must match")
    }

    @Test
    fun dumpBundleViews() = runTest {
        val f = java.io.File("/tmp/pmmap/views/uml-tree")
        if (!f.exists()) return@runTest
        val tree = UMFReader.read(f.readText())
        println("namespaces: ${tree.namespaces.map { it.name }}")
        val im = Namespace("calamus")
        val off = Namespace("official")
        for ((env, view) in listOf("client" to tree.envView("client"), "server" to tree.envView("server"))) {
            for (name in listOf("uk", "ow", "mu", "jh", "gv", "gs", "np", "na", "vm")) {
                println("$name@official[$env] -> ${view.map(off, im, n(name))}")
            }
        }
    }
}
