package xyz.wagyourtail.unimined.mapping.test.formats.ct

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.at.ATWriter
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.test.formats.tinyv2.TinyV2ReadWriteTest
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.formats.ct.CTReader
import xyz.wagyourtail.unimined.mapping.formats.ct.CTWriter
import xyz.wagyourtail.unimined.mapping.visitor.delegate.nsFiltered
import kotlin.test.Test
import kotlin.test.assertEquals

class CTReadWriteTest {
    val ctText = """
        classTweaker v2 intermediary
        accessible class net/minecraft/class_3720 # end of line comment
        
        # this is a comment
        accessible method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        
        # second comment
        extendable method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        mutable field net/minecraft/class_3721 field_19158 I
        
        # third comment
        inject-interface net/minecraft/class_3721 net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline
        inject-interface net/minecraft/class_3721 net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline2<TS;>
        
        # fourth comment
        extend-enum net/minecraft/class_3720 CONSTANT3
    """.trimIndent()

    @Test
    fun testRead() = runTest {
        val ct = Buffer().use {
            it.writeUtf8(ctText)
            CTReader.read(it, EnvType.JOINED)
        }

        val umfOut = Buffer().use {
            ct.accept(UMFWriter.write(it, EnvType.JOINED), sort = true)
            it.readUtf8()
        }

        assertEquals("""
umf	1	0
intermediary
c	net/minecraft/class_3720
	a	+	public	*	intermediary
c	net/minecraft/class_3721
	a	-	final	*	intermediary
	j	+	Lnet/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline2<TS;>;	intermediary
	j	+	Lnet/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline;	intermediary
	f	field_19158;I
		a	-	final	*	intermediary
	m	method_31659;(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
		a	+	final	+PRIVATE	intermediary
		a	+	protected	-PUBLIC	intermediary
		a	+	public	*	intermediary
		a	-	final	*	intermediary
""".trimStart(), umfOut)
    }

    @Test
    fun testReadWithTabs() = runTest {
        val aw = Buffer().use {
            it.writeUtf8(ctText.replace(" ", "\t"))
            CTReader.read(it, EnvType.JOINED)
        }
        val umfOut = Buffer().use {
            aw.accept(UMFWriter.write(it, EnvType.JOINED))
        }
    }

    @Test
    fun testIndirect() = runTest {
        val aw = Buffer().use {
            it.writeUtf8(ctText)
            CTReader.read(it, EnvType.JOINED)
        }

        val out = Buffer().use {
            aw.accept(CTWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals("""
        classTweaker v2 intermediary
        accessible class net/minecraft/class_3720
        extendable	class	net/minecraft/class_3721
        mutable field net/minecraft/class_3721 field_19158 I
        accessible method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        extendable method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        inject-interface net/minecraft/class_3721 net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline2<TS;>
        inject-interface net/minecraft/class_3721 net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline
        """.trimIndent().replace(" ", "\t"), out.trimEnd())
    }

    @Test
    fun testDirect() {
        val betterRead = CTReader.readData(StringCharReader(ctText))
        val betterWrite = buildString { CTWriter.writeData(betterRead, ::append) }
        assertEquals(ctText, betterWrite.trimEnd().replace("\t", " "))
    }

    @Test
    fun testRemap() = runTest {
        val m = Buffer().use { input ->
            input.writeUtf8(TinyV2ReadWriteTest.mappings)
            TinyV2Reader.read(input)
        }
        val betterRead = CTReader.readData(StringCharReader(ctText))
        val remapped = CTWriter.remapMappings(betterRead, m, Namespace("named"))
        val betterWrite = buildString { CTWriter.writeData(remapped, ::append) }

        assertEquals("""
        classTweaker	v2	named
        accessible	class	net/minecraft/block/entity/BlastFurnaceBlockEntity # end of line comment
        
        # this is a comment
        accessible	method	net/minecraft/block/entity/BellBlockEntity	serverTick	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/block/entity/BellBlockEntity;)V
        
        # second comment
        extendable	method	net/minecraft/block/entity/BellBlockEntity	serverTick	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/block/entity/BellBlockEntity;)V
        mutable	field	net/minecraft/block/entity/BellBlockEntity	resonateTime	I

        # third comment
        inject-interface net/minecraft/block/entity/BellBlockEntity net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline
        inject-interface net/minecraft/block/entity/BellBlockEntity net/fabricmc/fabric/api/client/rendering/v1/FabricRenderPipeline2<TS;>

        # fourth comment
        extend-enum net/minecraft/block/entity/BlastFurnaceBlockEntity CONSTANT3
        """.trimIndent().replace("\t", " "), betterWrite.trimEnd().replace("\t", " "))
    }

    @Test
    fun testAw2At() = runTest {
        val m = Buffer().use {
            it.writeUtf8(ctText)
            CTReader.read(it, EnvType.JOINED)
        }

        ATWriter.defaultToPublic = false
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

    @Test
    fun testAw2AtFixDefault() = runTest {
        val m = Buffer().use {
            it.writeUtf8(ctText)
            CTReader.read(it, EnvType.JOINED)
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

    @Test
    fun testWithExtraSpaces() = runTest {
        val betterRead = CTReader.readData(StringCharReader(ctText.replace(" ", "    ")))
        val betterWrite = buildString { CTWriter.writeData(betterRead, ::append) }
        assertEquals(ctText, betterWrite.trimEnd().replace("\t", " ").replace("    ", " "))
    }

    val awText = """
        accessWidener v2 intermediary
        accessible class net/minecraft/class_3720 # end of line comment
        
        # this is a comment
        accessible method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        
        # second comment
        extendable method net/minecraft/class_3721 method_31659 (Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        mutable field net/minecraft/class_3721 field_19158 I
    """.trimIndent()

    @Test
    fun testAWRead() = runTest {
        val ct = Buffer().use {
            it.writeUtf8(awText)
            CTReader.read(it, EnvType.JOINED)
        }

        val umfOut = Buffer().use {
            ct.accept(UMFWriter.write(it, EnvType.JOINED), sort = true)
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
		a	+	final	+PRIVATE	intermediary
		a	+	protected	-PUBLIC	intermediary
		a	+	public	*	intermediary
		a	-	final	*	intermediary
""".trimStart(), umfOut)
    }

}
