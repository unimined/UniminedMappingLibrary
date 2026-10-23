package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.*
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class MCPv6ReadWriteTest {

    val context = """
        umf	1	2
        searge	target
        c	a	net/minecraft/src/Minecraft
        	f	field_1_a	field1
        	m	func_1_a;()V	method1
        	m	func_2_a;(I)V	method2
    """.trimIndent()

    suspend fun readContext(): MutableMappingTree = UMFReader.read(context)

    @Test
    fun testFields() = runTest {
        val tree = readContext()
        MCPv6FieldReader.read(
            """
            searge,name,side,desc
            field_1_a,mcField,0,field docs
            field_2_a,serverField,1,
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
searge	target	mcp
c	a	net/minecraft/src/Minecraft	_
	f	field_1_a	field1	mcField
		*	"field docs"	mcp
	m	func_1_a;()V	method1	_
	m	func_2_a;(I)V	method2	_
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testMethods() = runTest {
        val tree = readContext()
        MCPv6MethodReader.read(
            """
            searge,name,side,desc
            func_1_a,mcMethod,0,method docs
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
searge	target	mcp
c	a	net/minecraft/src/Minecraft	_
	f	field_1_a	field1	_
	m	func_1_a;()V	method1	mcMethod
		*	"method docs"	mcp
	m	func_2_a;(I)V	method2	_
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testParams() = runTest {
        val tree = readContext()
        MCPv6ParamReader.read(
            """
            param,name,side
            p_1_0_,firstParam,0
            p_2_1_,secondParam,2
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
searge	target	mcp
c	a	net/minecraft/src/Minecraft	_
	f	field_1_a	field1	_
	m	func_1_a;()V	method1	_
		p	_	0	_	_	firstParam
	m	func_2_a;(I)V	method2	_
		p	_	1	_	_	secondParam
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testPackages() = runTest {
        val tree = MutableMappingTree()
        MCPv6PackageReader.read(
            """
            class,package
            Minecraft,net/minecraft/src
            World,net/minecraft/src
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	2
searge	mcp
c	net/minecraft/src/Minecraft	net/minecraft/src/Minecraft
c	net/minecraft/src/World	net/minecraft/src/World
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testFieldWrite() = runTest {
        val tree = UMFReader.read(
            """
            umf	1	2
            searge	mcp
            c	a	b
            	f	field_1_a	mcField
            		*	"field docs"	mcp
            	f	field_2_a	mcField2
            """.trimIndent()
        )

        val out = Buffer().use {
            tree.emit(MCPv6FieldWriter.write(it::writeUtf8))
            it.readUtf8()
        }

        assertEquals(
            """
searge,mcp,side,desc
field_1_a,mcField,2,field docs
field_2_a,mcField2,2,
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testMethodWrite() = runTest {
        val tree = UMFReader.read(
            """
            umf	1	2
            searge	mcp
            c	a	b
            	m	func_1_a;()V	mcMethod
            		*	"method docs"	mcp
            	m	func_2_a;(I)V	mcMethod2
            """.trimIndent()
        )

        val out = Buffer().use {
            tree.emit(MCPv6MethodWriter.write(it::writeUtf8))
            it.readUtf8()
        }

        assertEquals(
            """
searge,mcp,side,desc
func_1_a,mcMethod,2,method docs
func_2_a,mcMethod2,2,
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testParamWrite() = runTest {
        val tree = UMFReader.read(
            """
            umf	1	2
            searge	mcp
            c	a	b
            	m	func_1_a;(I)V	mcMethod
            		p	_	0	p_1_0_	firstParam
            """.trimIndent()
        )

        val out = Buffer().use {
            tree.emit(MCPv6ParamWriter.write(it::writeUtf8))
            it.readUtf8()
        }

        assertEquals(
            """
searge,mcp,side,desc
p_1_0_,firstParam,2
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
