package xyz.wagyourtail.unimined.mapping.test.formats.access

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.access.AccessFormatConvert
import xyz.wagyourtail.unimined.mapping.mappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AccessFormatConvert] — the 1.x `aw2at`/`at2aw`/`atLegacy2aw`/`mergeAws`
 * helpers as pure functions over the UML readers/writers.
 */
class AccessFormatConvertTest {

    private fun bytes(s: String) = s.encodeToByteArray()
    private fun text(b: ByteArray) = b.decodeToString()
    /** whitespace-normalized lines — writers emit tabs, samples use spaces */
    private fun lines(b: ByteArray) = text(b).lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() }
        .toList()

    private val aw = """
        accessWidener v2 named
        accessible class a/A
        accessible field a/A f I
        mutable field a/A g J
        accessible method a/A m ()V
        extendable method a/A n (I)V
        extendable class a/B
        transitive-accessible class a/T
    """.trimIndent()

    @Test
    fun `detect by filename and content`() {
        assertEquals(AccessFormatConvert.Format.AW,
            AccessFormatConvert.detect("modid.accesswidener", bytes(aw)))
        assertEquals(AccessFormatConvert.Format.AW,
            AccessFormatConvert.detect(null, bytes(aw)))
        val modern = "public net.minecraft.A f\n"
        assertEquals(AccessFormatConvert.Format.AT,
            AccessFormatConvert.detect("accesstransformer.cfg", bytes(modern)))
        assertEquals(AccessFormatConvert.Format.AT,
            AccessFormatConvert.detect("modid_at.cfg", bytes(modern)))
        assertEquals(AccessFormatConvert.Format.AT,
            AccessFormatConvert.detect(null, bytes(modern)))
        // `+f` rows detect as AT too
        assertEquals(AccessFormatConvert.Format.AT,
            AccessFormatConvert.detect(null, bytes("public+f net.minecraft.A f\n")))
        val legacy = "public-f net/minecraft/A.f\n"
        assertEquals(AccessFormatConvert.Format.LEGACY_AT,
            AccessFormatConvert.detect("accesstransformer.cfg", bytes(legacy)))
        assertEquals(AccessFormatConvert.Format.LEGACY_AT,
            AccessFormatConvert.detect(null, bytes(legacy)))
        assertNull(AccessFormatConvert.detect(null, bytes("classTweaker v1 named\n")))
        assertNull(AccessFormatConvert.detect(null, bytes("# only a comment\n")))
    }

    @Test
    fun `aw2at lowers access verbs`() {
        val out = AccessFormatConvert.aw2at(bytes(aw))
        assertEquals(listOf(
            "public a.A",
            "public a.A f",
            "public-f a.A g",
            "public a.A m()V",
            "protected-f a.A n(I)V",
            "public-f a.B",
            "public a.T",
        ), lines(out))
    }

    @Test
    fun `aw2at legacy emits dot-joined members`() {
        val out = AccessFormatConvert.aw2at(bytes(aw), legacy = true)
        assertEquals(listOf(
            "public a/A",
            "public a/A.f",
            "public-f a/A.g",
            "public a/A.m()V",
            "protected-f a/A.n(I)V",
            "public-f a/B",
            "public a/T",
        ), lines(out))
    }

    @Test
    fun `at2aw resolves field descs through the tree`() {
        val tree = mappingTree("named") {
            clazz("a/A") {
                field("f;I")
                field("g;J")
                method("m;()V")
                method("n;(I)V")
            }
            clazz("a/B")
        }
        // modern AT — class tokens are dotted; members space-separated
        val atDoc = """
            public-f a.A
            public a.A f
            private-f a.A g
            public-f a.A m()V
            protected a.A n(I)V
            public a.B
            private a.B
        """.trimIndent()
        val warns = mutableListOf<String>()
        val out = AccessFormatConvert.at2aw(
            bytes(atDoc), legacy = false, tree = tree,
            targetNs = Namespace("named"), warn = warns::add)
        assertEquals(listOf(
            "accessWidener v1 named",
            "extendable class a/A",
            "accessible field a/A f I",
            "mutable field a/A g J",
            "accessible method a/A m ()V",
            "extendable method a/A m ()V",
            "extendable method a/A n (I)V",
            "accessible class a/B",
        ), lines(out))
        // `private a.B` narrows — warned and skipped
        assertEquals(1, warns.size)
        assertTrue("a/B" in warns.single())
    }

    @Test
    fun `at2aw expands wildcards from the tree`() {
        val tree = mappingTree("named") {
            clazz("a/A") {
                field("f;I")
                field("g;J")
                method("m;()V")
                method("n;(I)V")
            }
        }
        val atDoc = """
            public a.A *
            public a.A *()
            protected a.A *(I)V
        """.trimIndent()
        val out = AccessFormatConvert.at2aw(
            bytes(atDoc), legacy = false, tree = tree,
            targetNs = Namespace("named"))
        assertEquals(listOf(
            "accessWidener v1 named",
            "accessible field a/A f I",
            "accessible field a/A g J",
            "accessible method a/A m ()V",
            "accessible method a/A n (I)V",
            // desc-filtered wildcard — only `n` matches `(I)V`
            "extendable method a/A n (I)V",
        ), lines(out))
    }

    @Test
    fun `legacy at converts to aw`() {
        val tree = mappingTree("named") {
            clazz("a/A") {
                field("f;I")
                method("m;()V")
            }
        }
        val atDoc = """
            public a/A.f
            public-f a/A.m()V
        """.trimIndent()
        val out = AccessFormatConvert.at2aw(
            bytes(atDoc), legacy = true, tree = tree,
            targetNs = Namespace("named"))
        assertEquals(listOf(
            "accessWidener v1 named",
            "accessible field a/A f I",
            "accessible method a/A m ()V",
            "extendable method a/A m ()V",
        ), lines(out))
    }

    @Test
    fun `at rewrite swaps syntax eras`() {
        val modern = "public-f net.minecraft.A m()V\n"
        val legacy = AccessFormatConvert.convert(
            bytes(modern), AccessFormatConvert.Format.AT,
            AccessFormatConvert.Format.LEGACY_AT)
        assertEquals(listOf("public-f net/minecraft/A.m()V"), lines(legacy))
        val back = AccessFormatConvert.convert(
            legacy, AccessFormatConvert.Format.LEGACY_AT,
            AccessFormatConvert.Format.AT)
        assertEquals(listOf("public-f net.minecraft.A m()V"), lines(back))
    }

    @Test
    fun `convert dispatches both directions`() {
        val atOut = AccessFormatConvert.convert(
            bytes(aw), AccessFormatConvert.Format.AW,
            AccessFormatConvert.Format.AT)
        assertEquals("public a.A", lines(atOut).first())
        val tree = mappingTree("named") { clazz("a/A") { field("f;I") } }
        val awOut = AccessFormatConvert.convert(
            bytes("public a.A f\n"), AccessFormatConvert.Format.AT,
            AccessFormatConvert.Format.AW, tree = tree)
        assertTrue(lines(awOut).contains("accessible field a/A f I"))
    }

    @Test
    fun `mergeAws dedups and upgrades version`() {
        val aw1 = """
            accessWidener v1 named
            accessible class a/A
            accessible field a/A f I
        """.trimIndent()
        val aw2 = """
            accessWidener v2 named
            accessible class a/A
            transitive-accessible class a/B
            accessible method a/B m ()V
        """.trimIndent()
        val out = AccessFormatConvert.mergeAws(
            listOf(bytes(aw1), bytes(aw2)), Namespace("named"))
        assertEquals(listOf(
            "accessWidener v2 named",
            "accessible class a/A",
            "accessible field a/A f I",
            "transitive-accessible class a/B",
            "accessible method a/B m ()V",
        ), lines(out))
    }

}
