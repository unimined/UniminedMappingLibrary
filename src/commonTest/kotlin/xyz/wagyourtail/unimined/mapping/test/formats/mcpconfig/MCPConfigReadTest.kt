package xyz.wagyourtail.unimined.mapping.test.formats.mcpconfig

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigAccessReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigConstructorReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigExceptionsReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigStaticMethodsReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals

class MCPConfigReadTest {

    @Test
    fun testAccess() = runTest {
        val tree = MutableMappingTree()
        MCPConfigAccessReader.read(
            """
            PUBLIC a func_1_a ()V
            PRIVATE b func_2_a (I)V
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
searge
c	a
	m	func_1_a;()V
		a	+	public	*
c	b
	m	func_2_a;(I)V
		a	+	private	*
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testConstructors() = runTest {
        val tree = MutableMappingTree()
        MCPConfigConstructorReader.read(
            """
            1 a ()V
            2 b (I)V
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
searge
c	a
	m	<init>;()V
c	b
	m	<init>;(I)V
		p	0	1	p_i2_1_
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testExceptions() = runTest {
        val tree = MutableMappingTree()
        MCPConfigExceptionsReader.read(
            """
            a/func_1_a ()V java/lang/Exception
            b/func_2_a (I)V java/io/IOException java/lang/Throwable
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        assertEquals(
            """
umf	1	3
searge
c	a
	m	func_1_a;()V
		x	+	java/lang/Exception	searge
c	b
	m	func_2_a;(I)V
		x	+	java/io/IOException	searge
		x	+	java/lang/Throwable	searge
            """.trimIndent(),
            out.trimEnd()
        )
    }

    @Test
    fun testStaticMethods() = runTest {
        val tree = UMFReader.read(
            """
            umf	1	3
            searge
            c	a
            	m	func_1_a;(I)V
            	m	func_2_a;(I)V
            """.trimIndent()
        )
        MCPConfigStaticMethodsReader.read(
            """
            func_1_a
            """.trimIndent(),
            tree,
        )

        val out = Buffer().use {
            tree.emit(UMFWriter.write(it, true), sort = true)
            it.readUtf8()
        }

        // func_1_a is listed static -> lvOrd starts at 0; func_2_a is not -> starts at 1
        assertEquals(
            """
umf	1	3
searge
c	a
	m	func_1_a;(I)V
		p	0	0	p_1_0
	m	func_2_a;(I)V
		p	0	1	p_2_1
            """.trimIndent(),
            out.trimEnd()
        )
    }

}
