package xyz.wagyourtail.unimined.mapping.test

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestUmfVersion {

    private val intermediary = Namespace("intermediary")

    @Test
    fun umf12UsesDistinctEnumExtensionKey() = runTest {
        val input = """
            umf	1	2
            intermediary
            c	clsA
            	z	+	EXTRA_CONST	intermediary
        """.trimIndent()
        val tree = Buffer().use { buf ->
            buf.writeUtf8(input)
            UMFReader.read(buf)
        }
        val cls = tree.getClass(intermediary, InternalName.unchecked("clsA"))!!
        assertEquals("EXTRA_CONST", cls.metadata.filterIsInstance<EnumExtensionElement>()
            .single().name.value)

        val output = Buffer().use { output ->
            tree.emit(UMFWriter.write(output, true), sort = true)
            output.readUtf8()
        }
        assertTrue(output.lines().any { it.startsWith("\tz\t+") })
        val tree2 = Buffer().use { UMFReader.read(Buffer().writeUtf8(output)) }
        assertEquals(
            "EXTRA_CONST",
            tree2.getClass(intermediary, InternalName.unchecked("clsA"))!!
                .metadata.filterIsInstance<EnumExtensionElement>().single().name.value
        )
    }

    @Test
    fun legacyUmf11EnumKeyStillReads() = runTest {
        val input = """
            umf	1	1
            intermediary
            c	clsA
            	u	+	EXTRA_CONST	intermediary
        """.trimIndent()
        val tree = Buffer().use { buffer ->
            buffer.writeUtf8(input)
            UMFReader.read(buffer)
        }
        val cls = tree.getClass(intermediary, InternalName.unchecked("clsA"))!!
        assertEquals(
            "EXTRA_CONST",
            cls.metadata.filterIsInstance<EnumExtensionElement>().single().name.value
        )
    }
}
