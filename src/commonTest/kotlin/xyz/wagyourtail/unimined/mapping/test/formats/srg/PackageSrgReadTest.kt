package xyz.wagyourtail.unimined.mapping.test.formats.srg

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.srg.PackageSrgReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class PackageSrgReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            ./ net/minecraft/src/
            com/example/ net/minecraft/world/
        """.trimIndent()

        val mappings = PackageSrgReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	1
source	target
k	""	net/minecraft/src/
k	com/example/	net/minecraft/world/
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
