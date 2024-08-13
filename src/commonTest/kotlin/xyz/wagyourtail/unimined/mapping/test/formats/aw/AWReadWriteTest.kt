package xyz.wagyourtail.unimined.mapping.test.formats.aw

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.at.ATWriter
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWWriter
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.test.formats.tinyv2.TinyV2ReadWriteTest
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.delegate.nsFiltered
import kotlin.test.Test
import kotlin.test.assertEquals

class AWReadWriteTest {
    val awText = """
        accessWidener v2 intermediary
        accessible class net/minecraft/class_3720
        accessible method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        extendable method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        mutable field net/minecraft/class_3721 field_19158 I
    """.trimIndent()

    @Test
    fun testRead() = runTest {
        val aw = Buffer().use {
            it.writeUtf8(awText)
            AWReader.read(it, EnvType.JOINED)
        }

        val umfOut = Buffer().use {
            aw.accept(UMFWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals("""
umf	1	0
intermediary
c	net/minecraft/class_3720
	a	+	public	*	intermediary
c	net/minecraft/class_3721
	a	-	final	*	intermediary
	f	field_19158;I
		a	-	final	*	intermediary
	m	method_31659;(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
		a	+	public	*	intermediary
		a	+	final	+PRIVATE	intermediary
		a	+	protected	-PUBLIC	intermediary
		a	-	final	*	intermediary
""".trimStart(), umfOut)
    }

    @Test
    fun testReadWithTabs() = runTest {
        val aw = Buffer().use {
            it.writeUtf8(awText.replace(" ", "\t"))
            AWReader.read(it, EnvType.JOINED)
        }
        val umfOut = Buffer().use {
            aw.accept(UMFWriter.write(it, EnvType.JOINED))
        }
    }

    @Test
    fun testIndirect() = runTest {
        val aw = Buffer().use {
            it.writeUtf8(awText)
            AWReader.read(it, EnvType.JOINED)
        }

        val out = Buffer().use {
            aw.accept(AWWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals("""
        accessWidener v2 intermediary
        accessible class net/minecraft/class_3720
        extendable	class	net/minecraft/class_3721
        mutable field net/minecraft/class_3721 field_19158 I
        accessible method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        extendable method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent().replace(" ", "\t"), out.trimEnd())
    }

    @Test
    fun testDirect() {
        val betterRead = AWReader.readData(CharReader(awText))
        val betterWrite = buildString { AWWriter.writeData(betterRead, ::append) }
        assertEquals(awText.replace(" ", "\t"), betterWrite.trimEnd())
    }

    @Test
    fun testRemap() = runTest {
        val m = Buffer().use { input ->
            input.writeUtf8(TinyV2ReadWriteTest.mappings)
            TinyV2Reader.read(input)
        }
        val betterRead = AWReader.readData(CharReader(awText))
        val remapped = AWWriter.remapMappings(betterRead, m, Namespace("named"))
        val betterWrite = buildString { AWWriter.writeData(remapped, ::append) }

        assertEquals("""
        accessWidener v2 named
        accessible	class	net/minecraft/block/entity/BlastFurnaceBlockEntity
        accessible	method	net/minecraft/block/entity/BellBlockEntity	serverTick	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/block/entity/BellBlockEntity;)V
        extendable	method	net/minecraft/block/entity/BellBlockEntity	serverTick	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/block/entity/BellBlockEntity;)V
        mutable	field	net/minecraft/block/entity/BellBlockEntity	resonateTime	I
        """.trimIndent().replace(" ", "\t"), betterWrite.trimEnd())
    }

    @Test
    fun testAw2At() = runTest {
        val m = Buffer().use {
            it.writeUtf8(awText)
            AWReader.read(it, EnvType.JOINED)
        }

        ATWriter.defaultToPublic = false
        val aw = Buffer().use {
            m.accept(ATWriter.write(it).nsFiltered("intermediary"))
//            m.accept(UMFWriter.write(EnvType.JOINED, it))
            it.readUtf8()
        }

        assertEquals("""
            public net.minecraft.class_3720
            default-f net.minecraft.class_3721
            default-f net.minecraft.class_3721 field_19158
            public-f net.minecraft.class_3721 method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent(), aw.trimEnd())

    }

    @Test
    fun testAw2AtFixDefault() = runTest {
        val m = Buffer().use {
            it.writeUtf8(awText)
            AWReader.read(it, EnvType.JOINED)
        }

        ATWriter.defaultToPublic = true
        val aw = Buffer().use {
            m.accept(ATWriter.write(it).nsFiltered("intermediary"))
//            m.accept(UMFWriter.write(EnvType.JOINED, it))
            it.readUtf8()
        }

        assertEquals("""
            public net.minecraft.class_3720
            public-f net.minecraft.class_3721
            public-f net.minecraft.class_3721 field_19158
            public-f net.minecraft.class_3721 method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent(), aw.trimEnd())

    }

}