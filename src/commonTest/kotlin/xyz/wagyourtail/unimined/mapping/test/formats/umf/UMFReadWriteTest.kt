package xyz.wagyourtail.unimined.mapping.test.formats.umf

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.tree.LazyMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals

class UMFReadWriteTest {

    @Test
    fun testReadWrite() = runTest {
        val inp = """
umf	1	0
intermediary	named
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	_
	m	method_1507;()V	testMethod;()V
		p	_	0	_	this
		v	1	_	_	lv1
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, false))
            output.readUtf8()
        }
        assertEquals(inp, output.trimEnd())
    }

    @Test
    fun testMergableMethods() = runTest {
        val inp = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	field_1724	_	_
	m	method_1507;()V	testMethod	_
		p	_	0	_	_	p1
	m	_	testMethod;()V	methodNameExtra
		p	_	0	_	this	_
		v	1	_	_	lv1	lv1Extra
	m	__	dontMerge;()V	dontMerge2
    m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>	<init>
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, false))
            output.readUtf8()
        }
        val testOutput = """umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	field_1724	_	_
	m	method_1507;()V	testMethod;()V	methodNameExtra;()V
		p	_	0	_	this	p1
		v	1	_	_	lv1	lv1Extra
	m	_;()V	dontMerge;()V	dontMerge2;()V
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V
        """.trimIndent().replace(' ', '\t').trimEnd()

        assertEquals(testOutput, output.trimEnd())
    }

    @Test
    fun testMinimize() = runTest {
        val inp = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	field_1724;Lnet/minecraft/class_746;	test;Lnamed/class;	_
	m	method_1507;()V	testMethod;()V	_
	m	__	dontMerge;()V	dontMerge2;()V
        """.trimIndent().replace(' ', '\t').trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, true))
            output.readUtf8()
        }
        val testOutput = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	field_1724;Lnet/minecraft/class_746;	test	_
	m	method_1507;()V	testMethod	_
	m	_;()V	dontMerge	dontMerge2
        """.trimIndent().replace(' ', '\t').trimEnd()

        assertEquals(testOutput, output.trimEnd())
    }

    @Test
    fun testLazy() = runTest {
        val inp = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	_
	m	method_1507;()V	testMethod
		p	_	0	_	this
		v	1	_	_	lv1
c	net/minecraft/class_310	_	net/minecraft/Minecraft
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            LazyMappingTree().also {
                UMFReader.read(input, it, EnvType.JOINED)
            }
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, false))
            output.readUtf8()
        }

        val testOuput = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	net/minecraft/Minecraft
	*	"example comment"	named
	f	field_1724	_	_
	m	method_1507;()V	testMethod;()V	_
		p	_	0	_	this	_
		v	1	_	_	lv1	_
        """.trimIndent()
        assertEquals(testOuput, output.trimEnd())

        val lazyOutput = Buffer().use { output ->
            mappings.lazyAccept(UMFWriter.write(output, true))
            output.readUtf8()
        }

        assertEquals("""
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	net/minecraft/Minecraft
	*	"example comment"	named
	f	field_1724	_	_
	m	method_1507;()V	testMethod	_
		p	_	0	_	this	_
		v	1	_	_	lv1	_
        """.trimIndent(), lazyOutput.trimEnd())
    }

    @Test
    fun testOverrideName() = runTest {
        val inp = """
umf	1	0
intermediary	named
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	_
	m	method_1507;()V	testMethod;()V
		p	_	0	_	this
		v	1	_	_	lv1
	m	method_1507;()V	nameOverride;()V
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, false))
            output.readUtf8()
        }
        assertEquals("""
umf	1	0
intermediary	named
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	_
	m	method_1507;()V	nameOverride;()V
		p	_	0	_	this
		v	1	_	_	lv1
        """.trimIndent(), output.trimEnd())
    }
}