package xyz.wagyourtail.unimined.mapping.test.formats.rgs

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.rgs.RetroguardReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class RetroguardReadTest {

    companion object {
        val inp = """
                .class_map a Minecraft
                .field_map a/a field_1724
                .method_map a/a ()V method_1507
                .class_map b World
                .field_map b/a field_1725
                .method_map b/a (I)V method_1204
            """.trimIndent()

        suspend fun readRetroguard(map: Map<String, String> = mapOf()): MutableMappingTree {
            return Buffer().use {
                it.writeUtf8(inp)
                RetroguardReader.read(it, nsMapping = map)
            }
        }

    }
    @Test
    fun testRead() = runTest {

        val mappings = readRetroguard()

        val output = Buffer().use { output ->
            mappings.emit(UMFWriter.write(output, minimize = true))
            output.readUtf8()
        }

        assertEquals("""
umf	1	1
source	target
k	""	net/minecraft/src/
c	a	net/minecraft/src/Minecraft
	f	a	field_1724
	m	a;()V	method_1507
c	b	net/minecraft/src/World
	f	a	field_1725
	m	a;(I)V	method_1204
""".trimIndent(), output.trimEnd())

    }

}