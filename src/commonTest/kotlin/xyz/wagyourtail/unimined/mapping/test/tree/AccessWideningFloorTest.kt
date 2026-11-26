package xyz.wagyourtail.unimined.mapping.test.tree

import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `AccessElement.apply` visibility invariants, and their fold through
 * [xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree.applyAccessChanges]:
 *
 * - **exclusivity** — adding a visibility displaces the others; the set never
 *   carries two (the pre-fix `public|protected` illegal-bit bug);
 * - **widening floor** — the FML/Forge `getFixedAccess` contract: rules only
 *   raise permissiveness (private < default < protected < public), `private`
 *   rules are no-ops, and `default` widens private but never demotes;
 * - **ordering** — re-adding a flag already present must still win over an
 *   intervening lower rule (set re-positioning, not insertion-order luck);
 * - **opt-out** — `widening = false` restores raw document semantics.
 */
class AccessWideningFloorTest {

    private fun add(set: MutableSet<AccessFlag>, flag: AccessFlag, widening: Boolean = true) {
        AccessElement(AddRemove.ADD, flag, AccessConditions.ALL).apply(set, widening)
    }

    private fun visOf(set: Set<AccessFlag>): AccessFlag =
        AccessFlag.visibility.singleOrNull { it in set } ?: AccessFlag.DEFAULT

    @Test
    fun `adding a visibility displaces the others`() {
        val set = mutableSetOf(AccessFlag.PUBLIC, AccessFlag.STATIC)
        add(set, AccessFlag.PROTECTED, widening = false)
        assertFalse(AccessFlag.PUBLIC in set, "public must be displaced")
        assertTrue(AccessFlag.PROTECTED in set)
        assertTrue(AccessFlag.STATIC in set, "non-visibility flags untouched")
    }

    @Test
    fun `floor never demotes public`() {
        for (target in listOf(AccessFlag.PROTECTED, AccessFlag.DEFAULT, AccessFlag.PRIVATE)) {
            val set = mutableSetOf(AccessFlag.PUBLIC)
            add(set, target)
            assertEquals(AccessFlag.PUBLIC, visOf(set), "$target must not demote public")
        }
    }

    @Test
    fun `floor never demotes protected below protected`() {
        for (target in listOf(AccessFlag.DEFAULT, AccessFlag.PRIVATE)) {
            val set = mutableSetOf(AccessFlag.PROTECTED)
            add(set, target)
            assertEquals(AccessFlag.PROTECTED, visOf(set), "$target must not demote protected")
        }
        val set = mutableSetOf(AccessFlag.PROTECTED)
        add(set, AccessFlag.PUBLIC)
        assertEquals(AccessFlag.PUBLIC, visOf(set), "public still widens protected")
    }

    @Test
    fun `floor widens private to any target`() {
        for ((target, expect) in listOf(
            AccessFlag.PUBLIC to AccessFlag.PUBLIC,
            AccessFlag.PROTECTED to AccessFlag.PROTECTED,
            AccessFlag.DEFAULT to AccessFlag.DEFAULT,
        )) {
            val set = mutableSetOf(AccessFlag.PRIVATE)
            add(set, target)
            assertEquals(expect, visOf(set), "private -> $target")
        }
    }

    @Test
    fun `private rules are no-ops, matching forge`() {
        for (start in listOf(AccessFlag.PUBLIC, AccessFlag.PROTECTED, AccessFlag.DEFAULT, AccessFlag.PRIVATE)) {
            val set = mutableSetOf<AccessFlag>()
            if (start != AccessFlag.DEFAULT) set += start
            add(set, AccessFlag.PRIVATE)
            assertEquals(start, visOf(set), "private rule on $start must no-op")
        }
    }

    @Test
    fun `default does not demote package-private or wider`() {
        val set = mutableSetOf<AccessFlag>() // package-private: no visibility bits
        add(set, AccessFlag.DEFAULT)
        assertEquals(AccessFlag.DEFAULT, visOf(set))
    }

    @Test
    fun `re-adding an existing visibility still wins after an intervening lower rule`() {
        // public member; rule1 protected, rule2 public — the final rule must win
        // even though PUBLIC was already in the original set (insertion-order trap)
        val set = mutableSetOf(AccessFlag.PUBLIC)
        add(set, AccessFlag.PROTECTED, widening = false)
        add(set, AccessFlag.PUBLIC, widening = false)
        assertEquals(AccessFlag.PUBLIC, visOf(set))
        assertEquals(1, set.count { it in AccessFlag.visibility }, "exactly one visibility")
    }

    @Test
    fun `widening false applies raw document semantics`() {
        val set = mutableSetOf(AccessFlag.PUBLIC)
        add(set, AccessFlag.PROTECTED, widening = false)
        assertEquals(AccessFlag.PROTECTED, visOf(set), "opt-out demotes")
    }

    @Test
    fun `non-visibility flags apply regardless of the floor`() {
        val set = mutableSetOf(AccessFlag.PUBLIC)
        AccessElement(AddRemove.ADD, AccessFlag.FINAL, AccessConditions.ALL).apply(set)
        assertTrue(AccessFlag.FINAL in set)
        AccessElement(AddRemove.REMOVE, AccessFlag.FINAL, AccessConditions.ALL).apply(set)
        assertFalse(AccessFlag.FINAL in set)
    }

    // ---- fold integration through applyAccessChanges ----

    private val intermediary = Namespace("intermediary")

    private val IT = """
        umf_it 1 0
        intermediary
        a/A java/lang/Object
         public m1 ()V
         private m2 ()V
        """.trimIndent()

    @Test
    fun `fold keeps protected rule off public method by default`() {
        val tree = mappingTree("intermediary", "named") {
            clazz("a/A", "nb/A") {
                method("m1;()V", "m1Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.PROTECTED, AccessConditions.ALL))
                }
                method("m2;()V", "m2Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.PUBLIC, AccessConditions.ALL))
                }
            }
        }
        val inheritance = CachedInheritanceTree(tree, StringCharReader(IT))
        inheritance.applyAccessChanges(tree)
        val info = inheritance.classInfo(InternalName.unchecked("a/A"))!!
        val m1 = info.methods.single { it.name.value == "m1" }
        val m2 = info.methods.single { it.name.value == "m2" }
        assertEquals(AccessFlag.PUBLIC.access, m1.access and AccessFlag.visibilityMask,
            "protected rule must not demote the public method")
        assertEquals(AccessFlag.PUBLIC.access, m2.access and AccessFlag.visibilityMask,
            "public rule must widen the private method")
        assertEquals(1, (m1.access and AccessFlag.visibilityMask).countOneBits(),
            "exactly one visibility bit on m1")
    }

    @Test
    fun `fold with widening false demotes`() {
        val tree = mappingTree("intermediary", "named") {
            clazz("a/A", "nb/A") {
                method("m1;()V", "m1Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.PROTECTED, AccessConditions.ALL))
                }
            }
        }
        val inheritance = CachedInheritanceTree(tree, StringCharReader(IT))
        inheritance.applyAccessChanges(tree, widening = false)
        val info = inheritance.classInfo(InternalName.unchecked("a/A"))!!
        val m1 = info.methods.single { it.name.value == "m1" }
        assertEquals(AccessFlag.PROTECTED.access, m1.access and AccessFlag.visibilityMask,
            "raw semantics demote")
    }
}
