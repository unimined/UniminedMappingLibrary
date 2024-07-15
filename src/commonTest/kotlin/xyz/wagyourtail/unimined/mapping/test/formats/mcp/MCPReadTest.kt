package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3ClassesReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1MethodReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.test.formats.rgs.RetroguardReadTest
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals

class MCPReadTest {

    @Test
    fun testOlderMethods() = runTest {
        val tree = RetroguardReadTest.readRetroguard(mapOf("target" to "searge"))

        Buffer().use {
            it.writeUtf8("""
                NULL,NULL,NULL
                NULL,NULL,NULL
                NULL,NULL,NULL
                class (ref),Reference,class (ref),Reference,Name,Notes,188
                a (Minecraft),method_1507,*,*,minecraftMethod,comment data,,[1]
                b (World),method_1204,*,*,worldMethod,comment data
            """.trimIndent())
            MCPv1MethodReader.read(EnvType.CLIENT, it, tree)
        }

        val out = Buffer().use {
            tree.accept(UMFWriter.write(EnvType.CLIENT, it, true))
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	0
source	searge	mcp
k	""	net/minecraft/src/	_
c	a	net/minecraft/src/Minecraft	_
	f	a	field_1724	_
	m	a;()V	method_1507	minecraftMethod
		*	"comment data"	mcp
c	b	net/minecraft/src/World	_
	f	a	field_1725	_
	m	a;(I)V	method_1204	worldMethod
		*	"comment data"	mcp
            """.trimIndent(),
            out.trimEnd()
        )

    }

    @Test
    fun testOlderFields() = runTest {
        val tree = RetroguardReadTest.readRetroguard(mapOf("target" to "searge"))

        Buffer().use {
            it.writeUtf8(
                """
                NULL,NULL,NULL
                NULL,NULL,NULL
                Class,Field,Name,Class,Field,Name,Name,Notes
                a,a,field_1724,*,*,minecraftField,comment data
                b,a,field_1725,*,*,worldField,comment data
            """.trimIndent()
            )
            MCPv1FieldReader.read(EnvType.CLIENT, it, tree)
        }

        val out = Buffer().use {
            tree.accept(UMFWriter.write(EnvType.CLIENT, it))
            it.readUtf8()
        }

        println(out)
    }

    val mcpClasses = """
        "name","notch","supername","package","side"
        "GuiConnectFailed","dw","GuiScreen","net/minecraft/src","0"
        "GuiConnecting","dv","GuiScreen","net/minecraft/src","0"
        "GuiDownloadTerrain","du","GuiScreen","net/minecraft/src","0"
        "GuiErrorScreen","dt","GuiScreen","net/minecraft/src","0"
        "ServerData","ds","Object","net/minecraft/src","1"
        "ServerWorld","dr","World","net/minecraft/src","1"
        "WorldClient","dq","World","net/minecraft/src","0"
        "World","dp","Object","net/minecraft/src","2"
    """.trimIndent()


    suspend fun readMCPClasses(): MemoryMappingTree {
        return Buffer().use {
            it.writeUtf8(mcpClasses)
            MCPv3ClassesReader.read(EnvType.CLIENT, it)
        }
    }

    @Test
    fun testClasses() = runTest {
        val tree = readMCPClasses()

        val out = Buffer().use {
            tree.accept(UMFWriter.write(EnvType.CLIENT, it))
            it.readUtf8()
        }

        assertEquals("""
                umf	1	0
                notch	searge
                c	dw	net/minecraft/src/GuiConnectFailed
                c	dv	net/minecraft/src/GuiConnecting
                c	du	net/minecraft/src/GuiDownloadTerrain
                c	dt	net/minecraft/src/GuiErrorScreen
                c	dq	net/minecraft/src/WorldClient
                c	dp	net/minecraft/src/World
            """.trimIndent(), out.trimEnd()
        )

    }


}