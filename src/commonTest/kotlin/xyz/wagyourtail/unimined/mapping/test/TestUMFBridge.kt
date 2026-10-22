package xyz.wagyourtail.unimined.mapping.test

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.SyntheticBridgeElement
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUMFBridge {

    private val input = """
        umf	1	2
        intermediary	named
        c	clsA	ClsA
        	m	methodA;()V	aM
        		b	iB	_
        """.trimIndent()

    @Test
    fun readsBridge() = runTest {
        val tree = Buffer().use { input ->
            input.writeUtf8(this@TestUMFBridge.input)
            UMFReader.read(input)
        }
        val method = tree.getClass(Namespace("intermediary"), InternalName.unchecked("clsA"))!!
            .methods.first()
        val bridges = method.metadata.filterIsInstance<SyntheticBridgeElement>()
        assertEquals(1, bridges.size)
        assertEquals("iB", bridges[0].names[Namespace("intermediary")]?.value)
        assertEquals(1, bridges[0].names.size)
    }

    @Test
    fun roundTripsBridge() = runTest {
        val tree = Buffer().use { input ->
            input.writeUtf8(this@TestUMFBridge.input)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            tree.emit(UMFWriter.write(output, true), sort = true)
            output.readUtf8()
        }
        assertEquals(input.trimEnd(), output.trimEnd())
    }

}
