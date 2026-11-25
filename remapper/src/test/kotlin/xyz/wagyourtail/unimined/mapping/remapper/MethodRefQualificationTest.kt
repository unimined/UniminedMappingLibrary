package xyz.wagyourtail.unimined.mapping.test.remapper

import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Canonical rendering of resolved `method=` selectors. Bare source names are
 * safe in dev namespaces only because their names stay long and unique; in
 * obfuscated production namespaces they can collapse onto unrelated members.
 * Once build-time resolution has proven the source member, the output keeps
 * that identity explicit (`Lowner;name(desc)`) unless the mixin is genuinely
 * multi-target, where synthesizing one owner would narrow the selector.
 */
class MethodRefQualificationTest {

    private fun context(withTree: xyz.wagyourtail.unimined.mapping.MutableMappingTree.() -> Unit = {}): ExtensionContext {
        val tree = treeOf()
        tree.withTree()
        return ExtensionContext(tree, obf, named)
    }

    private fun remapMethodRef(
        value: String,
        ctx: ExtensionContext,
        ownerHints: List<String>,
    ): Any? = ParamRemap.MethodRef.apply(value, ctx, ownerHints, null)

    @Test
    fun `bare unique method name renders fully qualified with resolved desc`() {
        val ctx = context {
            cls("a/Target", "x/Target").method("m_1;()V", "doIt;()V")
        }
        assertEquals("Lx/Target;doIt()V",
            remapMethodRef("m_1", ctx, listOf("a/Target")),
            "a unique bare selector must not survive as a collision-prone bare target name")
    }

    @Test
    fun `desc-bearing ownerless method ref also gains its unique owner`() {
        val ctx = context {
            cls("a/Target", "x/Target").method("m_1;(I)V", "doIt;(I)V")
        }
        assertEquals("Lx/Target;doIt(I)V",
            remapMethodRef("m_1(I)V", ctx, listOf("a/Target")))
    }

    @Test
    fun `already qualified method ref remaps without double qualification`() {
        val ctx = context {
            cls("a/Target", "x/Target").method("m_1;()V", "doIt;()V")
        }
        assertEquals("Lx/Target;doIt()V",
            remapMethodRef("La/Target;m_1()V", ctx, listOf("a/Target")))
    }

    @Test
    fun `ambiguous bare method name is not narrowed arbitrarily`() {
        val ctx = context {
            cls("a/Target", "x/Target").apply {
                method("m_1;()V", "doIt;()V")
                method("m_1;(I)V", "doIt;(I)V")
            }
        }
        assertEquals("m_1",
            remapMethodRef("m_1", ctx, listOf("a/Target")),
            "a genuinely ambiguous desc-less selector has no unique member to qualify")
    }

    @Test
    fun `multi-target mixin ref gains desc but stays ownerless`() {
        val ctx = context {
            cls("a/Target", "x/Target").method("m_1;()V", "doIt;()V")
            cls("a/Other", "x/Other")
        }
        assertEquals("doIt()V",
            remapMethodRef("m_1", ctx, listOf("a/Target", "a/Other")),
            "qualifying one owner would incorrectly exclude the other mixin targets")
    }

    @Test
    fun `ctor and target-wide wildcard names do not synthesize owners`() {
        val ctx = context {
            cls("a/Target", "x/Target")
            cls("a/Arg", "x/Arg")
        }
        assertEquals("<init>", remapMethodRef("<init>", ctx, listOf("a/Target")))
        assertEquals("<init>(Lx/Arg;)V",
            remapMethodRef("<init>(La/Arg;)V", ctx, listOf("a/Target")),
            "ctor descriptor class refs still remap, but the ctor remains ownerless")
        assertEquals("*", remapMethodRef("*", ctx, listOf("a/Target")))
    }

    @Test
    fun `unresolvable bare method name passes through unchanged`() {
        val ctx = context {
            cls("a/Target", "x/Target")
        }
        assertEquals("missing", remapMethodRef("missing", ctx, listOf("a/Target")))
    }
}
