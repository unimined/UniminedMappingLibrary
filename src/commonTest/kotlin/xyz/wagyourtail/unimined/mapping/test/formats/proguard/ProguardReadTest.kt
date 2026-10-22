package xyz.wagyourtail.unimined.mapping.test.formats.proguard

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.proguard.ProguardReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class ProguardReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            net.minecraft.src.Minecraft -> a:
                int field1 -> a
                net.minecraft.src.World world -> b
                void method1() -> a
                net.minecraft.src.World method2(int,boolean) -> b
        """.trimIndent()

        val mappings = ProguardReader.read(inp)

        val out = Buffer().use {
            mappings.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
source	target
c	net/minecraft/src/Minecraft	a
	f	field1;I	a
	f	world;Lnet/minecraft/src/World;	b
	m	method1;()V	a
	m	method2;(IZ)Lnet/minecraft/src/World;	b
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
