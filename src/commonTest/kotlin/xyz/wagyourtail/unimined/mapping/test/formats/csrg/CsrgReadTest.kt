package xyz.wagyourtail.unimined.mapping.test.formats.csrg

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.csrg.CsrgReader
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

class CsrgReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            a net/minecraft/src/Minecraft
            net/minecraft/src/Minecraft a field_1724
            net/minecraft/src/Minecraft a ()V method_1507
            b net/minecraft/src/World
            net/minecraft/src/World a field_1725
            net/minecraft/src/World a (I)V method_1204
        """.trimIndent()

        val mappings = CsrgReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
source	target
c	a	net/minecraft/src/Minecraft
	f	a	field_1724
	m	a;()V	method_1507
c	b	net/minecraft/src/World
	f	a	field_1725
	m	a;(I)V	method_1204
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testReadWithPackageContext() = runTest {
        // package.srg-style context: member owners are repackaged into the target ns
        val tree = MutableMappingTree()
        PackageSrgReader.read("./ pkga/", tree)

        val inp = """
            a pkga/Minecraft
            Minecraft a field_1724
            Minecraft a ()V method_1507
        """.trimIndent()
        CsrgReader.read(inp, tree)

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
source	target
k	""	pkga/
c	a	pkga/Minecraft
	f	a	field_1724
	m	a;()V	method_1507
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
