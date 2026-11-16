package xyz.wagyourtail.unimined.mapping.test.tree

import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.resolveAccessConditions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [resolveAccessConditions] — conditional `AccessElement`s evaluate
 * against the member's scanned bytecode flags and stamp back concrete
 * (`ALL`-condition) elements:
 *
 * - `m1` private, not final → `+PRIVATE` and `-FINAL` both hold;
 * - `m2` public → `+PRIVATE` fails and the element is dropped;
 * - `f1` private+volatile → `+VOLATILE` holds;
 * - `m3` has no scan counterpart → its conditional element is left
 *   untouched (no flag set to test against).
 */
class ResolveAccessConditionsTest {

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")

    private val IT = """
        umf_it 1 0
        intermediary
        a/A java/lang/Object
         private m1 ()V
         public m2 ()V
         private|volatile f1 I
        """.trimIndent()

    private fun cond(text: String) = AccessConditions.unchecked(text)

    private fun fixture(): Pair<MutableMappingTree, CachedInheritanceTree> {
        val tree = mappingTree("intermediary", "named") {
            clazz("a/A", "nb/A") {
                method("m1;()V", "m1Named") {
                    metadata(
                        AccessElement(AddRemove.ADD, AccessFlag.FINAL, cond("+PRIVATE")),
                        AccessElement(AddRemove.ADD, AccessFlag.SYNCHRONIZED, cond("-FINAL")),
                    )
                }
                method("m2;()V", "m2Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.FINAL, cond("+PRIVATE")))
                }
                field("f1;I", "f1Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.TRANSIENT, cond("+VOLATILE")))
                }
                method("m3;()V", "m3Named") {
                    metadata(AccessElement(AddRemove.ADD, AccessFlag.FINAL, cond("+PRIVATE")))
                }
            }
        }
        return tree to CachedInheritanceTree(tree, StringCharReader(IT))
    }

    private fun MutableMappingTree.accessElements(cls: String, member: String): List<AccessElement> {
        val c = getClass(intermediary, InternalName.unchecked(cls))!!
        return c.methods.filter { it.entries[intermediary]?.name?.value == member }
            .flatMap { it.metadata.filterIsInstance<AccessElement>() } +
                c.fields.filter { it.entries[intermediary]?.name?.value == member }
                    .flatMap { it.metadata.filterIsInstance<AccessElement>() }
    }

    @Test
    fun holdsBecomeConcreteFailsAreDropped() {
        val (tree, inheritance) = fixture()
        // m1: 2 resolved; m2: 1 dropped; f1: 1 resolved; m3: unresolved
        assertEquals(4, tree.resolveAccessConditions(intermediary, inheritance))

        val m1 = tree.accessElements("a/A", "m1")
        assertEquals(2, m1.size)
        assertTrue(m1.all { e -> e.condition == AccessConditions.ALL })
        assertEquals(
            setOf(AccessFlag.FINAL, AccessFlag.SYNCHRONIZED),
            m1.map { e -> e.value }.toSet()
        )

        assertTrue(tree.accessElements("a/A", "m2").isEmpty(),
            "failed condition — the element must not become unconditional")

        val f1 = tree.accessElements("a/A", "f1")
        assertEquals(1, f1.size)
        assertEquals(AccessFlag.TRANSIENT, f1.single().value)
        assertEquals(AccessConditions.ALL, f1.single().condition)

        val m3 = tree.accessElements("a/A", "m3")
        assertEquals(1, m3.size)
        assertEquals(cond("+PRIVATE"), m3.single().condition,
            "no scanned member — the conditional stays unevaluated")
    }

    @Test
    fun unconditionalElementsPassThrough() {
        val tree = mappingTree("intermediary", "named") {
            clazz("a/A", "nb/A") {
                method("m1;()V", "m1Named") {
                    metadata(AccessElement(AddRemove.REMOVE, AccessFlag.FINAL, AccessConditions.ALL))
                }
            }
        }
        val inheritance = CachedInheritanceTree(tree, StringCharReader(IT))
        assertEquals(0, tree.resolveAccessConditions(intermediary, inheritance))
        val m1 = tree.accessElements("a/A", "m1")
        assertEquals(1, m1.size)
        assertEquals(AddRemove.REMOVE, m1.single().type)
    }

}
