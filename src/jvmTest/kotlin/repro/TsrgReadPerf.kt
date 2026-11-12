package repro

import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.source
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import java.io.File
import kotlin.test.Test

/**
 * Perf probe for the forge-userdev `mappings.read` 15.4 s finding —
 * splits TSRG parse cost (discard sink) from tree-insert cost
 * (MutableMappingTree). Run:
 *   ./gradlew jvmTest --tests repro.TsrgReadPerf -Ptsrg=<path>
 */
class TsrgReadPerf {

    private class DiscardSink : MappingSink {
        override fun push(element: MappingElement) = element
        override fun push(meta: MetadataElement) = meta
        override fun pop(element: MappingElement) {}
    }

    private class CountingSink(val into: MappingSink) : MappingSink {
        val byType = sortedMapOf<String, Int>()
        override fun push(element: MappingElement): MappingElement? {
            byType.merge(element::class.simpleName ?: "?", 1, Int::plus)
            return into.push(element)
        }
        override fun push(meta: MetadataElement): MetadataElement? {
            byType.merge("meta:" + (meta::class.simpleName ?: "?"), 1, Int::plus)
            return into.push(meta)
        }
        override fun pop(element: MappingElement) = into.pop(element)
    }

    private val file = File(
        System.getenv("TSRG") ?: System.getProperty("tsrg")
            ?: "/run/media/william/m2shared/Projects/unimined-org/unimined" +
                    "/testing/perf/.cold-guh/caches/unimined/forge/mcpconfig" +
                    "/mcp_config-1.20.1-20230612.114412/joined/_data/config" +
                    "/joined.tsrg"
    )

    private suspend fun readInto(into: MappingSink, label: String, unchecked: Boolean = false) {
        val text = file.readText()
        val reader = FormatRegistry.autodetectFormat(
            file.name, file.inputStream().source().buffer()
        )!!.reader
        val t0 = System.nanoTime()
        reader.read(text, null, into, emptyMap(), FormatReaderSettings(unchecked = unchecked))
        println("[$label] ${(System.nanoTime() - t0) / 1_000_000}ms")
    }

    @Test
    fun splitParseVsInsert() = runTest {
        if (!file.exists()) {
            println("[skip] $file not present — set TSRG to a real mapping file")
            return@runTest
        }
        // floor: read text + naive line split (tokenize bound)
        repeat(2) { i ->
            val t0 = System.nanoTime()
            val n = file.readText().lines().size
            println("[lines-${i + 1}] ${(System.nanoTime() - t0) / 1_000_000}ms ($n lines)")
        }
        // CharReader token stream bound — same reads the parser does
        // (whitespace + tokens per line) minus element construction
        repeat(2) { i ->
            val text = file.readText()
            val r = xyz.wagyourtail.commonskt.reader.StringCharReader(text)
            var tokens = 0
            val t0 = System.nanoTime()
            while (!r.exhausted()) {
                if (r.peek() == '\n') { r.take(); continue }
                r.takeWhitespace()
                while (r.peek() != null && r.peek() != '\n') {
                    if (r.takeNext() != null) tokens++
                }
            }
            println("[tokenize-${i + 1}] ${(System.nanoTime() - t0) / 1_000_000}ms ($tokens tokens)")
        }
        // warmup + measure discard (pure parse+alloc)
        readInto(DiscardSink(), "discard-1")
        readInto(DiscardSink(), "discard-2")
        readInto(DiscardSink(), "discard-unchecked-1", unchecked = true)
        readInto(DiscardSink(), "discard-unchecked-2", unchecked = true)
        // warmup + measure real tree (parse + push/merge)
        readInto(MutableMappingTree(), "tree-1")
        readInto(MutableMappingTree(), "tree-2")
        readInto(MutableMappingTree(), "tree-unchecked-1", unchecked = true)
        readInto(MutableMappingTree(), "tree-unchecked-2", unchecked = true)
        // canonical sink skips post-pop merge trials — models the
        // trusted-materialization / serialized-payload read path
        readInto(MutableMappingTree.canonical(), "tree-canonical-1", unchecked = true)
        var loaded = MutableMappingTree.canonical()
        readInto(loaded, "tree-canonical-2", unchecked = true)
        // compose.emit attribution: emit into empty (pure insert) vs into a
        // populated copy (every name collides → merge trials fire). The
        // delta isolates merge cost inside a compose-style emit.
        val into1 = MutableMappingTree()
        var t0 = System.nanoTime()
        loaded.emit(into1)
        println("[emit-empty-1] ${(System.nanoTime() - t0) / 1_000_000}ms")
        val into2 = MutableMappingTree()
        loaded.emit(into2)
        t0 = System.nanoTime()
        loaded.emit(into2)
        println("[emit-merge-1] ${(System.nanoTime() - t0) / 1_000_000}ms")
        // umf text-emit floor — proves text emit is cheap (~250ms): the
        // derive cost is tree construction, not serialization
        val sb1 = StringBuilder()
        t0 = System.nanoTime()
        loaded.emit(
            xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter.write(sb1::append, false),
            nsFilter = loaded.namespaces.toList()
        )
        println("[umf-serial] ${(System.nanoTime() - t0) / 1_000_000}ms (${sb1.length} chars)")
        // umfb: serialize the loaded tree, re-read into a fresh canonical
        // tree, and prove losslessness by re-emitting both to umf text
        val bin = okio.Buffer()
        t0 = System.nanoTime()
        loaded.emit(xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.write(bin), sort = true)
        val bytes = bin.readByteArray()
        println("[umfb-write] ${(System.nanoTime() - t0) / 1_000_000}ms (${bytes.size} bytes)")
        // release the source tree — both trees + payload can't co-reside
        // without heavy GC skewing the read numbers
        loaded = MutableMappingTree.canonical()
        System.gc()
        // count the event stream each path feeds the tree
        val c1 = CountingSink(DiscardSink())
        readInto(c1, "umf-events", unchecked = true)
        println("[umf-events] $c1".replace("CountingSink", ""))
        println(c1.byType)
        val c2 = CountingSink(DiscardSink())
        t0 = System.nanoTime()
        xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.read(bytes, c2)
        println("[umfb-events] ${(System.nanoTime() - t0) / 1_000_000}ms")
        println(c2.byType)
        // isolate: umfb parse+alloc alone (no tree insert)
        t0 = System.nanoTime()
        xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.read(bytes, DiscardSink())
        println("[umfb-discard] ${(System.nanoTime() - t0) / 1_000_000}ms")
        var back = MutableMappingTree()
        t0 = System.nanoTime()
        xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.read(bytes, back)
        println("[umfb-read-plain] ${(System.nanoTime() - t0) / 1_000_000}ms")
        back = MutableMappingTree.canonical()
        repeat(2) { i ->
            back = MutableMappingTree.canonical()
            t0 = System.nanoTime()
            xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.read(bytes, back)
            println("[umfb-read-${i + 1}] ${(System.nanoTime() - t0) / 1_000_000}ms")
        }
        // identical-check removed — corpus test proves losslessness;
        // the emit+sort pair OOM'd the test heap
        Unit
    }
}
