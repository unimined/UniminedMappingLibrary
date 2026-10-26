package xyz.wagyourtail.unimined.mapping.test.formats.feather

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.feather.ExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.feather.SignatureReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatherReadTest {

    @Test
    fun testExceptions() = runTest {
        val inp = """
            a
            	m ()V java/lang/Exception java/lang/Throwable
            b
            	m (I)V java/io/IOException
        """.trimIndent()

        val mappings = ExceptionReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
source
c	a
	m	m;()V
		x	+	java/lang/Exception	source
		x	+	java/lang/Throwable	source
c	b
	m	m;(I)V
		x	+	java/io/IOException	source
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testSignatures() = runTest {
        val inp = """
            a <T:Ljava/lang/Object;>Ljava/lang/Object;
            	m ()V (TT;)V
            	f I TT;
        """.trimIndent()

        val mappings = SignatureReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
source
c	a
	g	<T:Ljava/lang/Object;>Ljava/lang/Object;	source
	f	f;I
		g	TT;	source
	m	m;()V
		g	(TT;)V	source
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
