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
	f	c	field_71512_c	
	f	c;Ljava/util/Map;	_	c;Ljava/util/Map;
	f	c;Ljava/util/Map;	_	field_3;Ljava/util/Map;
	m	method_1507	testMethod	_
		p	_	0	_	_	p1
	m	_	testMethod;()V	ignored
		p	_	0	_	this	_
		v	1	_	_	lv1	lv1Extra
	m	_	testMethod;()V	methodNameExtra
	m	__	dontMerge;()V	dontMerge2
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	_	_
		p	0	_	p1	_	_
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	_	<init>
		p	_	2	p2	_	_
	m	<init>	<init>	<init>
		p	0	1	_	_	test
		p	1	2	_	_
        """.trimIndent().trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, false), sort = true)
            output.readUtf8()
        }
        val testOutput = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	c	field_71512_c	c
	f	c;Ljava/util/Map;	field_71512_c;Ljava/util/Map;	field_3;Ljava/util/Map;
	f	field_1724	_	_
	m	<init>	<init>	<init>
		p	0	1	_	_	test
		p	1	2	_	_	_
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V
		p	0	1	p1	_	test
		p	1	2	p2	_	_
	m	_;()V	dontMerge;()V	dontMerge2;()V
	m	method_1507	testMethod	ignored
		p	_	0	_	_	p1
	m	method_1507;()V	testMethod;()V	methodNameExtra;()V
		p	_	0	_	this	p1
		v	1	_	_	lv1	lv1Extra
        """.trimIndent().replace(' ', '\t').trimEnd()

        assertEquals(testOutput, output.trimEnd())
    }

    @Test
    fun testMergableParams() = runTest {
        val inp = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V
		p	0	_	p1	_	_
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>	<init>
		p	0	1	_	_	_
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
	m	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V	<init>;(Leqm;JLeub;Ljava/lang/String;Ljava/lang/String;)V
		p	0	1	p1	_	_
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
            mappings.accept(UMFWriter.write(output, true), sort = true)
            output.readUtf8()
        }
        val testOutput = """
umf	1	0
intermediary	named	extra
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
	f	field_1724;Lnet/minecraft/class_746;	test	_
	m	_;()V	dontMerge	dontMerge2
	m	method_1507;()V	testMethod	_
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
            mappings.nonLazyAccept(UMFWriter.write(output, false))
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

    @Test
    fun testAddDescInfo() = runTest {
        val inp = """
umf	1	0
intermediary	named
c	net/minecraft/class_310	net/minecraft/client/MinecraftClient
	*	"example comment"	named
	f	field_1724	fieldName
		*	"comment"	named
	f	field_1724;Z	_
	m	method_1507	testMethod
		p	_	0	_	this
		v	1	_	_	lv1
	m	method_1507;()V	nameOverride
		*	"comment"	named
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
	f	field_1724	fieldName
		*	comment	named
	f	field_1724;Z	fieldName;Z
		*	comment	named
	m	method_1507	testMethod
		p	_	0	_	this
		v	1	_	_	lv1
	m	method_1507;()V	nameOverride;()V
		*	comment	named
		p	_	0	_	this
		v	1	_	_	lv1
        """.trimIndent(), output.trimEnd())
    }
}