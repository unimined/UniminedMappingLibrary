package repro

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.impl.dollarInnerClassNames
import xyz.wagyourtail.unimined.mapping.tree.impl.renest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FG2 feather `__` inner markers: the composed tree exported
 * `net/minecraft/util/profiler/Profiler$Profiler__Result` into
 * `srg2mcp.srg`, which `FMLDeobfuscatingRemapper` fed back as a class
 * name — `ClassNotFoundException` on the joined double name.
 *
 * Root cause: `fixNest` took the whole `Outer__Inner` leaf as the
 * inner simple name, producing `outer$Outer__Inner`. Read-time
 * [dollarInnerClassNames] normalization (declared per source column)
 * turns the cells into `$` names before renest/export ever see them,
 * and the clsName fix keeps the inner simple name for any `__` cell
 * that survives normalization.
 */
class FeatherDollarInnerRepro {

    private fun iname(s: String) = InternalName.unchecked(s)

    private val official = Namespace("official")
    private val searge = Namespace("searge")
    private val calamus = Namespace("calamus")
    private val feather = Namespace("feather")

    private fun profilerTree(): MutableMappingTree {
        val tree = MutableMappingTree()
        // parent first — renest resolves it by exact-name index probe
        tree.clazz(mapOf(
            official to iname("x"),
            searge to iname("net/minecraft/profiler/Profiler"),
            calamus to iname("C_8525326"),
            feather to iname("net/minecraft/util/profiler/Profiler"),
        ))
        tree.clazz(mapOf(
            official to iname("x\$y"),
            searge to iname("net/minecraft/profiler/Profiler\$Result"),
            calamus to iname("C_2451641"),
            feather to iname("net/minecraft/util/profiler/Profiler__Result"),
        ))
        return tree
    }

    /** The exact compose-time chain: official `$` fires renest into
     *  every dst column; the `__` feather leaf must yield the inner
     *  simple name, not a doubled join. */
    @Test
    fun renestSplitsFeatherLeafAtMarker() = runTest {
        val tree = profilerTree()
        tree.renest(official, setOf(searge, calamus, feather))
        tree.renest(calamus, setOf(feather))
        val row = tree.getClass(official, iname("x\$y"))
        assertEquals(
            "net/minecraft/util/profiler/Profiler\$Result",
            row?.names?.get(feather)?.value
        )
        assertEquals(
            "net/minecraft/profiler/Profiler\$Result",
            row?.names?.get(searge)?.value
        )
    }

    /** Read-time normalization: `__` cells become `$` so compose and
     *  exports never see the marker — renest then no-ops on the row. */
    @Test
    fun dollarInnerClassNamesNormalizesBeforeRenest() = runTest {
        val tree = profilerTree()
        tree.dollarInnerClassNames(setOf(feather))
        val row = tree.getClass(official, iname("x\$y"))
        assertEquals(
            "net/minecraft/util/profiler/Profiler\$Result",
            row?.names?.get(feather)?.value
        )
        // the row keeps its identity — other columns untouched
        assertEquals("C_2451641", row?.names?.get(calamus)?.value)

        tree.renest(official, setOf(searge, calamus, feather))
        tree.renest(calamus, setOf(feather))
        assertEquals(
            "net/minecraft/util/profiler/Profiler\$Result",
            tree.getClass(official, iname("x\$y"))?.names?.get(feather)?.value
        )
    }

    /** Non-marker columns and cells without the separator pass through. */
    @Test
    fun dollarInnerClassNamesLeavesOtherColumns() = runTest {
        val tree = profilerTree()
        tree.dollarInnerClassNames(setOf(feather))
        assertTrue(
            tree.classes.none {
                it.names.values.any { n -> n.value.contains("__") }
            }
        )
        tree.dollarInnerClassNames(setOf(searge, calamus))
        // re-running with no markers left is a no-op
        assertEquals(
            "net/minecraft/profiler/Profiler\$Result",
            tree.getClass(official, iname("x\$y"))?.names?.get(searge)?.value
        )
    }
}
