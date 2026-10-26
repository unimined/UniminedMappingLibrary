package xyz.wagyourtail.unimined.mapping.test.formats.tinyv1

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.tiny.v1.TinyV1Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class TinyV1ReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            v1	notch	searge	mcp
            CLASS	a	net/minecraft/src/Minecraft	Minecraft
            FIELD	a	I	a	field_1	field1
            METHOD	a	()V	a	method_1	method1
        """.trimIndent()

        val mappings = TinyV1Reader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
notch	searge	mcp
c	a	net/minecraft/src/Minecraft	Minecraft
	f	a;I	field_1	field1
	m	a;()V	method_1	method1
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
