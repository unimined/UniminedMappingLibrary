package xyz.wagyourtail.unimined.mapping.test.formats.nests

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.nests.NestReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class NestReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            aba	aez			aba	9
            abj	xy			abj	1
            abn	kx	a	(Ljava/lang/Iterable;)Ljava/util/List;	1	8
            abt	nc			abt	9
            ac	rp			ac	9
            acc	px	<clinit>	()V	3	8
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            NestReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output))
            output.readUtf8()
        }
        assertEquals("""
umf	1	0
source
c	aba
	i	i	aba;Laez;
		a	add	static	source
c	abj
	i	i	abj;Lxy;
c	abn
	i	a	1;Lkx;a;(Ljava/lang/Iterable;)Ljava/util/List;
		a	add	static	source
c	abt
	i	i	abt;Lnc;
		a	add	static	source
c	ac
	i	i	ac;Lrp;
		a	add	static	source
c	acc
	i	a	3;Lpx;<clinit>;()V
		a	add	static	source
""".trim(), output.trimEnd())
    }
    
    
}