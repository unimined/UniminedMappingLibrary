package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3MethodReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class MCPv3ReadTest {

    @Test
    fun testFields() = runTest {
        val tree = MutableMappingTree()
        MCPv3FieldReader.read(
            """
            "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
            "field_1_a","mcField","a","La;","La;","Minecraft","Minecraft","net/minecraft/src","0"
            "field_2_a","mcField2","b","Lb;","Lb;","World","World","net/minecraft/src","2"
            "field_3_a","serverOnly","c","Lc;","Lc;","World","World","net/minecraft/src","1"
            """.trimIndent(),
            tree,
            settings = FormatReaderSettings(envType = EnvType.CLIENT)
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, EnvType.CLIENT), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	1
notch	searge	mcp
c	net/minecraft/src/Minecraft	_	_
	f	a	field_1_a	mcField
c	net/minecraft/src/World	_	_
	f	b	field_2_a	mcField2
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testMethods() = runTest {
        val tree = MutableMappingTree()
        MCPv3MethodReader.read(
            """
            "searge","name","notch","sig","notchsig","classname","classnotch","package","side"
            "func_1_a","mcMethod","a","()V","()V","Minecraft","Minecraft","net/minecraft/src","0"
            "func_2_a","mcMethod2","b","(I)V","(I)V","World","World","net/minecraft/src","2"
            "func_3_a","serverOnly","c","()V","()V","World","World","net/minecraft/src","1"
            """.trimIndent(),
            tree,
            settings = FormatReaderSettings(envType = EnvType.CLIENT)
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, EnvType.CLIENT), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	1
notch	searge	mcp
c	net/minecraft/src/Minecraft	_	_
	m	a;()V	func_1_a;()V	mcMethod;()V
c	net/minecraft/src/World	_	_
	m	b;(I)V	func_2_a;(I)V	mcMethod2;(I)V
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
