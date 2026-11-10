package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6MethodReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * forgeBuiltinMCP pipeline shape: an official→searge srg read followed by a
 * methods.csv overlay relabeled to `forgeMCP`. sided csv rows must land their
 * dst names under `forgeMCP:env` — not `mcp:env` (the emit-stage nsRenamer has
 * to be env-aware) — and env-tag the src cols along the way.
 */
class MCPv6SidedRepro {

    @Test
    fun sidedCsvAttachesQualifiedDst() = runTest {
        val tree = MutableMappingTree()
        SrgReader.read(
            """
            CL: a net/minecraft/client/Minecraft
            MD: a/a (FZII)V a/func_73830_a (FZII)V
            MD: a/b ()V a/func_71407_l ()V
            """.trimIndent(),
            tree,
            mapOf("source" to "official", "target" to "searge"),
        )
        MCPv6MethodReader.read(
            """
            searge,name,side,desc
            func_73830_a,renderGameOverlay,0,client-only method
            func_71407_l,runTick,2,joined method
            """.trimIndent(),
            tree,
            mapOf("mcp" to "forgeMCP"),
        )
        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }
        assertEquals(
            """
umf	1	3
official	official:client	searge	searge:client	forgeMCP	forgeMCP:client
c	a	_	a	_	_	_
	m	a;(FZII)V	a	func_73830_a	func_73830_a	_	renderGameOverlay
		*	"client-only method"	forgeMCP:client
	m	b;()V	_	func_71407_l	_	runTick	_
		*	"joined method"	forgeMCP
            """.trimIndent(),
            out.trimEnd()
        )
    }
}
