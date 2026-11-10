package repro

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.impl.renest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fg2 `srgRuntime` corruption: `SummonedEntityTrigger$Instance`
 * (`az$b`) shipped unrenamed and `CrashReport` (`b`) gained a bogus
 * `Instance of SummonedEntityTrigger` inner-class element — the
 * emitted jar threw `IncompatibleClassChangeError` on boot.
 *
 * Root cause: `clazz` upserts resolved through `getClass`'s
 * flat-name fallback — `getClass(official, "b")` (a real top-level
 * class not yet inserted) aliased to `az$b`'s row because `*$b` was
 * unique; the upsert then rekeyed `az$b` as `b`, losing the original
 * class and transferring its inner-class metadata. `clazz` and the
 * renest parent probe now use `getClassExact` (index-only).
 */
class Fg2RenestRepro {

    private fun iname(s: String) = InternalName.unchecked(s)

    /** Synthesized form — the row-insertion order from the real
     *  fg2 UMF (`az$b` before `b`), no fixture file needed. */
    @Test
    fun upsertMustNotAliasInnerLastSegment() = runTest {
        val tree = UMFReader.read("umf\t1\t3\nofficial\tsearge\tmcp\n") as MutableMappingTree
        val official = Namespace("official")
        val searge = Namespace("searge")
        val mcp = Namespace("mcp")

        // az$a / az$b / az — the trigger's rows, parsed first
        tree.clazz(mapOf(
            official to iname("az\$a"),
            searge to iname("net/x/SummonedEntityTrigger\$Listeners"),
            mcp to iname("net/x/SummonedEntityTrigger\$Listeners"),
        ))
        val azb = tree.clazz(mapOf(
            official to iname("az\$b"),
            searge to iname("net/x/SummonedEntityTrigger\$Instance"),
            mcp to iname("net/x/SummonedEntityTrigger\$Instance"),
        ))
        tree.clazz(mapOf(
            official to iname("az"),
            searge to iname("net/x/SummonedEntityTrigger"),
            mcp to iname("net/x/SummonedEntityTrigger"),
        ))

        // `b` is a real top-level class parsed AFTER az$b — the
        // flat fallback must not return az$b's row for it
        val b = tree.clazz(mapOf(
            official to iname("b"),
            searge to iname("net/y/CrashReport"),
            mcp to iname("net/y/CrashReport"),
        ))

        assertTrue(azb !== b, "clazz(b) returned az\$b's row")
        assertEquals("az\$b", azb.names[official]?.value)
        assertEquals(
            "net/x/SummonedEntityTrigger\$Instance",
            azb.names[searge]?.value
        )
        assertEquals("b", b.names[official]?.value)
        assertNotNull(tree.getClass(official, iname("az\$b")))
        assertNotNull(tree.getClass(official, iname("b")))
    }

    /** Replays [addExtraInnerClassMappings] on the real fg2
     *  modsource tree when the preserved bundle is available. */
    @Test
    fun replayRenestOnRealTree() = runTest {
        val f = File("/tmp/fg2-tree/views/uml-tree")
        if (!f.exists()) return@runTest
        val tree = UMFReader.read(f.readText()) as MutableMappingTree
        val official = Namespace("official")

        // the 9 classes forge-binpatch adds — same insertion as
        // ExtraInnerClassMappings
        for (name in listOf(
            "ain$22", "anh\$FlowerEntry", "anp$1", "anp\$EmeraldGenerator",
            "awu\$Builder", "aym\$WorldSleepResult", "c$4", "c$5", "ot$1"
        )) {
            if (tree.getClass(official, iname(name)) != null) continue
            tree.clazz(mapOf(official to iname(name)))
        }
        tree.renest(official, tree.namespaces.toSet() - official)

        assertNotNull(tree.getClass(official, iname("az\$b")))
        assertNull(
            tree.getClass(official, iname("b"))!!.metadata
                .filterIsInstance<InnerClassElement>().firstOrNull(),
            "CrashReport row gained an inner-class element"
        )
        // no top-level row may carry inner-class metadata
        val polluted = tree.classes.filter {
            val s = it.names[official]?.value ?: return@filter false
            '$' !in s && it.metadata.filterIsInstance<InnerClassElement>().isNotEmpty()
        }
        assertEquals(emptyList(), polluted.map { it.names[official]?.value })
    }
}
