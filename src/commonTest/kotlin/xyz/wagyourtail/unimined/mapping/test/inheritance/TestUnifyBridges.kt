package xyz.wagyourtail.unimined.mapping.test.inheritance

import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * [MemberGroups.unifyBridges] — javac `ACC_BRIDGE` members union with
 * the unique same-name sibling whose descriptor narrows the bridge's
 * (and only when the bridge's own signature overrides a supertype).
 */
class TestUnifyBridges {

    /**
     * - `a/Sub implements i/Cmp` — canonical javac shape: erased bridge
     *   `cmp(Ljava/lang/Object;)I` + specialized `cmp(La/Cmp;)I`.
     * - `a/Renamed extends a/GenBase` — obfuscated shape: the bridge
     *   keeps the interface-signature name `compareTo` while the
     *   specialized target is renamed `a` — no shared name, only the
     *   relaxed narrowing filter unifies.
     * - `a/Ambig` — two narrowing siblings (`La/Cmp;`, `La/Other;`), no
     *   unique target → skipped.
     * - `a/NoFlag` — same shape but the bridge flag is gone (obfuscator-
     *   stripped) — scan data has no method bodies, so undetectable.
     */
    private val IT = """
        umf_it 1 0
        intermediary
        i/Cmp java/lang/Object
         public|abstract cmp (Ljava/lang/Object;)I
        a/Sub java/lang/Object i/Cmp
         public|synthetic|bridge cmp (Ljava/lang/Object;)I
         public cmp (La/Cmp;)I
        a/Cmp java/lang/Object
        a/GenBase java/lang/Object java/lang/Comparable
         public compareTo (Ljava/lang/Object;)I
        a/Renamed a/GenBase
         public|synthetic|bridge compareTo (Ljava/lang/Object;)I
         public a (La/Renamed;)I
        a/Ambig java/lang/Object i/Cmp
         public|synthetic|bridge cmp (Ljava/lang/Object;)I
         public cmp (La/Cmp;)I
         public cmp (La/Other;)I
        a/Other java/lang/Object
        a/NoFlag java/lang/Object i/Cmp
         public cmp (Ljava/lang/Object;)I
         public cmp (La/Cmp;)I
        """.trimIndent()

    private fun groups(): Pair<CachedInheritanceTree, MemberGroups> {
        val tree = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        return tree to MemberGroups(tree)
    }

    @Test
    fun bridgeUnifiesWithUniqueNarrowingSibling() {
        val (tree, groups) = groups()
        assertEquals(2, groups.unifyBridges())
        val sub = tree.classes[InternalName.unchecked("a/Sub")]!!
        val bridge = sub.methods.single { it.isBridge }
        val spec = sub.methods.single { !it.isBridge }
        assertSame(
            groups.methodGroup(sub, bridge),
            groups.methodGroup(sub, spec),
            "bridge and specialized target must share one component"
        )
    }

    @Test
    fun renamedSpecializedTargetUnifies() {
        val (tree, groups) = groups()
        groups.unifyBridges()
        val renamed = tree.classes[InternalName.unchecked("a/Renamed")]!!
        val bridge = renamed.methods.single { it.isBridge }
        val spec = renamed.methods.single { !it.isBridge }
        assertSame(
            groups.methodGroup(renamed, bridge),
            groups.methodGroup(renamed, spec),
            "differently-named narrowing target must union with the bridge"
        )
    }

    @Test
    fun ambiguousSpecializedCandidatesAreSkipped() {
        val (tree, groups) = groups()
        groups.unifyBridges()
        val ambig = tree.classes[InternalName.unchecked("a/Ambig")]!!
        val bridge = ambig.methods.single { it.isBridge }
        for (spec in ambig.methods.filter { !it.isBridge }) {
            assertNotSame(
                groups.methodGroup(ambig, bridge),
                groups.methodGroup(ambig, spec),
                "two narrowing siblings — no unique forward target"
            )
        }
    }

    @Test
    fun flaglessBridgeShapeIsNotDetected() {
        val (tree, groups) = groups()
        groups.unifyBridges()
        val noFlag = tree.classes[InternalName.unchecked("a/NoFlag")]!!
        val erased = noFlag.methods.single { it.descriptor.value == "(Ljava/lang/Object;)I" }
        val spec = noFlag.methods.single { it.descriptor.value == "(La/Cmp;)I" }
        assertNotSame(
            groups.methodGroup(noFlag, erased),
            groups.methodGroup(noFlag, spec),
            "no ACC_BRIDGE — the forwarding body scan data can't recover"
        )
    }

    @Test
    fun unificationIsIdempotent() {
        val (_, groups) = groups()
        assertEquals(2, groups.unifyBridges())
        assertEquals(0, groups.unifyBridges())
    }

}
