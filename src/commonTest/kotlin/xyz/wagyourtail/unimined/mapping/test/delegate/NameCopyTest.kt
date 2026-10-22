package xyz.wagyourtail.unimined.mapping.test.delegate

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.fillMissingNames
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class NameCopyTest {

    val ORIGINAL = """
        umf 1 0
        intermediary named extra
        c a b
         f a;Z b
         m a;()V b
        c d
         f d;Z
        """.trimIndent()

    val FILLED_EXTRA = """
        umf 1 1
        intermediary named extra
        c a b b
         f a;Z b b
         m a;()V b b
        c d d d
         f d;Z d d
        """.trimIndent()

    @Test
    fun testNameCopy() = runTest{
        val mappings = Buffer().use { input ->
            input.writeUtf8(ORIGINAL)
            UMFReader.read(input)
        }

        mappings.fillMissingNames(Namespace("intermediary") to setOf(Namespace("named")))

        val output = Buffer().use { output ->
            mappings.fillMissingNames(Namespace("named") to setOf(Namespace("extra")))
            mappings.emit(UMFWriter.write(output, true))
            output.readUtf8()
        }

        assertEquals(FILLED_EXTRA.trimEnd(), output.trimEnd().replace('\t', ' '))

    }

    @Test
    fun testParallelTreeVersion() = runTest {
        val mappings = Buffer().use { input ->
            input.writeUtf8(ORIGINAL)
            UMFReader.read(input)
        }

        mappings.fillMissingNames(Namespace("intermediary") to setOf(Namespace("named")), Namespace("named") to setOf(Namespace("extra")))

        val output = Buffer().use { output ->
            mappings.emit(UMFWriter.write(output, true))
            output.readUtf8()
        }

        assertEquals(FILLED_EXTRA.trimEnd(), output.trimEnd().replace('\t', ' '))
    }

}