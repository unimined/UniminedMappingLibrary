package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.runBlocking
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Perf harness for [AsmRemapper] against a real merged Minecraft jar.
 *
 * Skipped unless `-Dasmremap.perf.jar=<merged jar>` and
 * `-Dasmremap.perf.mappings=<tiny v2 file>` (or the ASMREMAP_PERF_*
 * env equivalents) point at real artifacts — meant to be run manually
 * (`--tests "*Perf*"`) when profiling remap changes.
 *
 * Exercises the graph-propagation path (MemberGroups +
 * GraphNamespaceRemapper) — the old materializing [AsmRemapper.overlay]
 * is retained for callers that need rows but isn't benchmarked here.
 */
class AsmRemapperPerfTest {

    private fun heapMB() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_000_000

    @Test
    fun `remap merged mc jar`() = runBlocking {
        fun prop(name: String, env: String) =
            System.getProperty(name) ?: System.getenv(env)
        val jar = prop("asmremap.perf.jar", "ASMREMAP_PERF_JAR")?.let(Paths::get)
        val mappings = prop("asmremap.perf.mappings", "ASMREMAP_PERF_MAPPINGS")?.let(Paths::get)
        if (jar == null || mappings == null || !Files.exists(jar) || !Files.exists(mappings)) {
            println("skipping: set -Dasmremap.perf.jar/-Dasmremap.perf.mappings")
            return@runBlocking
        }
        val official = Namespace("official")
        val mojmap = Namespace("mojmap")

        val text: String
        println("file read: ${measureTime {
            text = String(Files.readAllBytes(mappings), StandardCharsets.UTF_8)
        }}")

        val tree = TinyV2Reader.read(text)
        repeat(3) { System.gc() }
        println("tree retained: heap=${heapMB()}MB")
        println("tree read: ${measureTime { TinyV2Reader.read(text) }}; heap=${heapMB()}MB")

        val out = Files.createTempFile("remap-perf", ".jar")
        println("remap end-to-end (scan+groups+remap+write): ${measureTime {
            AsmRemapper.remap(jar, out, listOf(tree), official, mojmap,
                libraries = setOf(jar), options = Options())
        }}; heap=${heapMB()}MB")

        repeat(3) { System.gc() }
        println("post-gc heap=${heapMB()}MB; out=${Files.size(out) / 1_000_000}MB")
        Files.deleteIfExists(out)
    }

}
