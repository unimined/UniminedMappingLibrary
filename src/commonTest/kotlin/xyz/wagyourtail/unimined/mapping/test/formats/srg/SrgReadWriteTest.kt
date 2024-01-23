package xyz.wagyourtail.unimined.mapping.test.formats.srg

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class SrgReadWriteTest {

    @Test
    fun test() = runTest {
        val inp = """
            PK: . net/minecraft/src
            PK: net net
            CL: a net/minecraft/block/Block
            MD: a/a (Lb;IIILc;)Z net/minecraft/block/Block/func_72275_a (Lnet/minecraft/block/BlockBed;IIILnet/minecraft/block/BlockButton;)Z
            CL: b net/minecraft/block/BlockBed
            FD: b/a net/minecraft/block/BlockBed/bedBlockID
            CL: c net/minecraft/block/BlockButton
        """.trimIndent()
        val mappings = Buffer().use {
            it.writeUtf8(inp)
            SrgReader.read(it)
        }
        val output = Buffer().use {
            mappings.accept(SrgWriter.write(it))
            it.readUtf8()
        }
        assertEquals(inp, output.trimEnd())
    }

}