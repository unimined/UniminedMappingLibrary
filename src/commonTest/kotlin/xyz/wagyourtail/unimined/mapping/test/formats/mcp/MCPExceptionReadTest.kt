package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.mcp.MCPExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class MCPExceptionReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            a.b()V=java/lang/Exception|p_1_0_-Access=PUBLIC
            net/minecraft/src/World.func_2_a(I)V=java/io/IOException,java/lang/Throwable|p_2_0_,p_2_1_
        """.trimIndent()

        val mappings = MCPExceptionReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
searge
c	a
	m	b;()V
		a	+	public	*
		x	+	java/lang/Exception	searge
		p	0	_	p_1_0_
c	net/minecraft/src/World
	m	func_2_a;(I)V
		x	+	java/io/IOException	searge
		x	+	java/lang/Throwable	searge
		p	0	_	p_2_0_
		p	1	_	p_2_1_
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
