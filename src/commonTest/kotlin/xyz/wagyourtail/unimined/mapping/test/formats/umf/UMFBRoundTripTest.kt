package xyz.wagyourtail.unimined.mapping.test.formats.umf

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFB
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.test.formats.FirstPartyCorpus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UMFB must be lossless for the entire element universe: UMF-read →
 * UMFB-write → UMFB-read → UMF-write has to reproduce the input document.
 * The corpus test drives every firstparty fixture (params, locals,
 * signatures, inner classes, exceptions, access, annotations, constants,
 * wildcards, constant groups) through the binary round-trip.
 */
class UMFBRoundTripTest {

    private fun readUmf(text: String): MutableMappingTree =
        MutableMappingTree().also { tree ->
            UMFReader.readNonBlocking(
                FormatReaderSettings(unchecked = true),
                StringCharReader(text), null, tree, emptyMap()
            )
        }

    private suspend fun dumpUmf(tree: MutableMappingTree): String =
        Buffer().use { tree.emit(UMFWriter.write(it, minimize = true)); it.readUtf8().trimEnd() }

    private suspend fun roundTrip(tree: MutableMappingTree): String {
        val bin = Buffer()
        tree.emit(UMFB.write(bin))
        val back = MutableMappingTree.canonical()
        UMFB.read(bin.readByteArray(), back)
        return dumpUmf(back)
    }

    @Test
    fun testBasic() = runTest {
        val inp = """
umf	1	3
intermediary	named
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	_
	m	method_1507;()V	testMethod;()V
		p	_	0	_	this
		v	1	_	_	lv1
        """.trimIndent().trimEnd()
        // invariant: the round-tripped tree dumps identically to the
        // directly-read tree (dump is minimized — byte-equality with the
        // unminimized input is not the contract)
        assertEquals(dumpUmf(readUmf(inp)), roundTrip(readUmf(inp)))
    }

    @Test
    fun corpusRoundTrip() = runTest {
        for ((name, dump) in FirstPartyCorpus.all()) {
            assertEquals(dump, roundTrip(readUmf(dump)), "umfb round-trip mismatch for $name")
        }
    }
}
