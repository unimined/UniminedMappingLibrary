package xyz.wagyourtail.unimined.mapping.test.formats.tsrg

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV1Reader
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV1Writer
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class TsrgReadWriteTest {

    val v1inp = """
        a net/minecraft/src/Minecraft
        	a field_1724
        	a ()V method_1507
        b net/minecraft/src/World
        	a field_1725
        	a (I)V method_1204
    """.trimIndent()

    @Test
    fun testV1ReadWrite() = runTest {
        val mappings = TsrgV1Reader.read(v1inp)

        val output = Buffer().use {
            mappings.emit(TsrgV1Writer.write(it::writeUtf8))
            it.readUtf8()
        }
        assertEquals(v1inp, output.trimEnd())
    }

    val v2inp = """
        tsrg2 obf mcp
        a net/minecraft/src/Minecraft
        	a ()V method_1507
        		0 param0_s param0_t
        	a field_1724
        b net/minecraft/src/World
        	a (I)V method_1204
    """.trimIndent()

    @Test
    fun testV2Read() = runTest {
        val mappings = TsrgV2Reader.read(v2inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
obf	mcp
c	a	net/minecraft/src/Minecraft
	f	a	field_1724
	m	a;()V	method_1507
		p	0	_	param0_s	param0_t
c	b	net/minecraft/src/World
	m	a;(I)V	method_1204
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
