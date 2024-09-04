package xyz.wagyourtail.unimined.mapping.test.formats.at

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader
import xyz.wagyourtail.unimined.mapping.formats.at.ATWriter
import xyz.wagyourtail.unimined.mapping.formats.at.LegacyATReader
import xyz.wagyourtail.unimined.mapping.formats.at.LegacyATWriter
import xyz.wagyourtail.unimined.mapping.formats.aw.AWWriter
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.test.formats.tinyv2.TinyV2ReadWriteTest
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.visitor.delegate.nsFiltered
import kotlin.test.Test
import kotlin.test.assertEquals

class ATReadWriteTest {
    val atText = """
        public+f net.minecraft.class_3720 # class
        public-f net.minecraft.class_3720 * # all fields
        public net.minecraft.class_3720 *() # all methods
        # other class
        protected-f net.minecraft.class_3721
        public-f net.minecraft.class_3721 method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        private net.minecraft.class_3721 field_19158
    """.trimIndent()

    val legacyAtText = """
        public+f net/minecraft/class_3720 # class
        public-f net/minecraft/class_3720.* # all fields
        public net/minecraft/class_3720.*() # all methods
        # other class
        protected-f net/minecraft/class_3721
        public-f net/minecraft/class_3721.method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        private net/minecraft/class_3721.field_19158
    """.trimIndent()

    val umfText = """
        umf	1	0
        source
        c	net/minecraft/class_3720
        	a	+	public	*	source
        	a	+	final	*	source
        	w	f	_
        		a	+	public	*	source
        		a	-	final	*	source
        	w	m	_
        		a	+	public	*	source
        c	net/minecraft/class_3721
        	a	+	protected	*	source
        	a	-	final	*	source
        	f	field_19158
        		a	+	private	*	source
        	m	method_31659;(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        		a	+	public	*	source
        		a	-	final	*	source
    """.trimIndent()

    @Test
    fun testRead() = runTest {
        val at = Buffer().use {
            it.writeUtf8(atText)
            ATReader.read(it)
        }

        val umfOut = Buffer().use {
            at.accept(UMFWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals(umfText, umfOut.trimEnd())
    }

    @Test
    fun testLegacyRead() = runTest {
        val at = Buffer().use {
            it.writeUtf8(legacyAtText)
            LegacyATReader.read(it)
        }

        val umfOut = Buffer().use {
            at.accept(UMFWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals(umfText, umfOut.trimEnd())
    }

    @Test
    fun testIndirect() = runTest {
        val at = Buffer().use {
            it.writeUtf8(atText)
            ATReader.read(it)
        }

        val out = Buffer().use {
            at.accept(ATWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals("""
            public+f net.minecraft.class_3720
            public-f net.minecraft.class_3720 *
            public net.minecraft.class_3720 *()
            protected-f net.minecraft.class_3721
            private net.minecraft.class_3721 field_19158
            public-f net.minecraft.class_3721 method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent(), out.trimEnd())
    }

    @Test
    fun testLegacyIndirect() = runTest {
        val at = Buffer().use {
            it.writeUtf8(legacyAtText)
            LegacyATReader.read(it)
        }

        val out = Buffer().use {
            at.accept(LegacyATWriter.write(it, EnvType.JOINED))
            it.readUtf8()
        }

        assertEquals("""
            public+f net/minecraft/class_3720
            public-f net/minecraft/class_3720.*
            public net/minecraft/class_3720.*()
            protected-f net/minecraft/class_3721
            private net/minecraft/class_3721.field_19158
            public-f net/minecraft/class_3721.method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent(), out.trimEnd())
    }

    @Test
    fun testDirect() = runTest {
        val betterRead = ATReader.readData(StringCharReader(atText))
        val betterWrite = buildString { ATWriter.writeData(betterRead, ::append) }
        assertEquals("""
            public+f net.minecraft.class_3720
            public-f net.minecraft.class_3720 *
            public net.minecraft.class_3720 *()
            protected-f net.minecraft.class_3721
            public-f net.minecraft.class_3721 method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
            private net.minecraft.class_3721 field_19158
        """.trimIndent(), betterWrite.trimEnd())
    }

    @Test
    fun testLegacyDirect() = runTest {
        val betterRead = LegacyATReader.readData(StringCharReader(legacyAtText))
        val betterWrite = buildString { LegacyATWriter.writeData(betterRead, ::append) }
        assertEquals("""
            public+f net/minecraft/class_3720
            public-f net/minecraft/class_3720.*
            public net/minecraft/class_3720.*()
            protected-f net/minecraft/class_3721
            public-f net/minecraft/class_3721.method_31659(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
            private net/minecraft/class_3721.field_19158
        """.trimIndent(), betterWrite.trimEnd())
    }

    @Test
    fun testRemap() = runTest {
        val m = Buffer().use { input ->
            input.writeUtf8(TinyV2ReadWriteTest.mappings)
            TinyV2Reader.read(input)
        }
        val betterRead = LegacyATReader.readData(StringCharReader(legacyAtText))
        val remapped = ATWriter.remapMappings(betterRead, m, Namespace("intermediary"), Namespace("named"))
        val betterWrite = buildString { ATWriter.writeData(remapped, ::append) }
        assertEquals("""
           public+f net.minecraft.block.entity.BlastFurnaceBlockEntity
           public-f net.minecraft.block.entity.BlastFurnaceBlockEntity *
           public net.minecraft.block.entity.BlastFurnaceBlockEntity *()
           protected-f net.minecraft.block.entity.BellBlockEntity
           public-f net.minecraft.block.entity.BellBlockEntity serverTick(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/block/entity/BellBlockEntity;)V
           private net.minecraft.block.entity.BellBlockEntity resonateTime
        """.trimIndent(), betterWrite.trimEnd())
    }

    @Test
    fun testAt2Aw() = runTest {
        val m = Buffer().use { input ->
            input.writeUtf8(TinyV2ReadWriteTest.mappings)
            TinyV2Reader.read(input)
        }
        Buffer().use {
            it.writeUtf8(atText)
            ATReader.read(it, m, m, EnvType.JOINED, mapOf("source" to "intermediary"))
        }

        val aw = Buffer().use {
            m.accept(AWWriter.write(it).nsFiltered("intermediary"))
//            m.accept(UMFWriter.write(EnvType.JOINED, it))
            it.readUtf8()
        }

        assertEquals("""
            accessWidener	v2	intermediary
            accessible	class	net/minecraft/class_3720
            accessible	method	net/minecraft/class_3720	<init>	(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V
            extendable	class	net/minecraft/class_3721
            accessible	method	net/minecraft/class_3721	method_31659	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
            extendable	method	net/minecraft/class_3721	method_31659	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V
        """.trimIndent(), aw.trimEnd())

    }

}