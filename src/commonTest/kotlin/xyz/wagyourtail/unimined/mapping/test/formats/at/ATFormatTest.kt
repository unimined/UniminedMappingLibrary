package xyz.wagyourtail.unimined.mapping.test.formats.at

import xyz.wagyourtail.unimined.mapping.formats.at.ATFormat
import kotlin.test.Test
import kotlin.test.assertEquals

/** Disambiguation matrix for [ATFormat.detect] — the first unambiguous
 *  line decides; a bare internal-name class token (`a/b/C`) is
 *  ambiguous since class-only entries are identical in both formats. */
class ATFormatTest {

    private fun detect(vararg lines: String) = ATFormat.detect(lines.asSequence())

    @Test
    fun `modern dotted class entries`() {
        assertEquals(ATFormat.Kind.MODERN, detect(
            "public net.minecraft.world.World",
            "public net.minecraft.world.World field_175725_v"
        ))
    }

    @Test
    fun `modern whitespace member`() {
        assertEquals(ATFormat.Kind.MODERN, detect(
            "public net.minecraft.world.World func_175707_a()Lnet/minecraft/util/math/BlockPos;"
        ))
    }

    @Test
    fun `modern internal names`() {
        // regression: `/` in the class token is NOT a legacy signal when
        // the member is whitespace-separated — modern ATReader accepts
        // internal names (it normalizes `.` -> `/` itself)
        assertEquals(ATFormat.Kind.MODERN, detect(
            "# comment",
            "public a/b/C",
            "public a/b/C doIt()V",
            "protected-f a/b/C",
            "public-f a/b/C state"
        ))
    }

    @Test
    fun `legacy desc joined`() {
        assertEquals(ATFormat.Kind.LEGACY, detect(
            "public a/b/C.doIt()V"
        ))
    }

    @Test
    fun `legacy field dot joined`() {
        assertEquals(ATFormat.Kind.LEGACY, detect(
            "public a/b/C.state"
        ))
    }

    @Test
    fun `legacy file disambiguates on member lines`() {
        assertEquals(ATFormat.Kind.LEGACY, detect(
            "public a/b/C",
            "public a/b/C.state",
            "protected-f a/b/C.doIt()V"
        ))
    }

    @Test
    fun `bare internal name class only is ambiguous defaults modern`() {
        // class-only entries parse identically in both formats, so the
        // `/`-only heuristic must not decide — default MODERN
        assertEquals(ATFormat.Kind.MODERN, detect(
            "public a/b/C",
            "protected-f a/b/D"
        ))
    }

    @Test
    fun `comment tail does not defeat either signal`() {
        assertEquals(ATFormat.Kind.LEGACY, detect("public a/b/C.m()V # note"))
        assertEquals(ATFormat.Kind.MODERN, detect("public a.b.C field # note"))
    }

    @Test
    fun `comments and blanks alone default modern`() {
        assertEquals(ATFormat.Kind.MODERN, detect(
            "# only comments",
            "",
            "#and more"
        ))
    }

}
