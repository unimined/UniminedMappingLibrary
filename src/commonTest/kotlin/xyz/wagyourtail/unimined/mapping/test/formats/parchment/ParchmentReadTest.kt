package xyz.wagyourtail.unimined.mapping.test.formats.parchment

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.parchment.ParchmentReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class ParchmentReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            {
              "version": "1.0.0",
              "packages": [
                { "name": "net/minecraft/src", "javadoc": "package docs" }
              ],
              "classes": [
                {
                  "name": "net/minecraft/src/Minecraft",
                  "methods": [
                    {
                      "name": "method1",
                      "descriptor": "(I)V",
                      "javadoc": ["method docs", "second line"],
                      "parameters": [
                        { "index": 0, "name": "param0", "javadoc": "param docs" }
                      ]
                    }
                  ],
                  "fields": [
                    { "name": "field1", "descriptor": "I", "javadoc": "field docs" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val mappings = ParchmentReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
source
k	net/minecraft/src/
	*	"package docs"	source
c	net/minecraft/src/Minecraft
	f	field1;I
		*	"field docs"	source
	m	method1;(I)V
		*	"method docs\nsecond line"	source
		p	_	0	param0
			*	"param docs"	source
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
