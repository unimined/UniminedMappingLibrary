package xyz.wagyourtail.unimined.mapping.test.tree

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.DeltaMappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeltaMappingTreeTest {

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")

    /** clsA with one mapped method + one mapped field, clsB subclassing it
     *  with the same members mapped only in fns */
    private fun base(): MutableMappingTree {
        val tree = MutableMappingTree(setOf(intermediary, named))
        val clsA = tree.clazz(mapOf(
            intermediary to InternalName.unchecked("clsA"),
            named to InternalName.unchecked("NamedA"),
        ))
        clsA.method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V"),
        ))
        clsA.field(mapOf(
            intermediary to FieldNameAndDescriptor.unchecked("fieldA;I"),
            named to FieldNameAndDescriptor.unchecked("aF;I"),
        ))
        val clsB = tree.clazz(mapOf(
            intermediary to InternalName.unchecked("clsB"),
            named to InternalName.unchecked("NamedB"),
        ))
        clsB.method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
        ))
        return tree
    }

    private fun cls(tree: MutableMappingTree, name: String) =
        tree.getClass(intermediary, InternalName.unchecked(name))!!

    @Test
    fun readsFallThroughToBase() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        // class lookup resolves the base row
        val clsA = cls(d, "clsA")
        assertEquals("NamedA", clsA.names[named]?.value)
        // member lookups see base entries
        with(d) {
            assertEquals("aM", clsA.getMethod(intermediary,
                UnqualifiedName.unchecked("methodA"),
                xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor.unchecked("()V")
            )!!.names[named]?.value)
        }
        // iterating surfaces base rows
        assertEquals(setOf("clsA", "clsB"), d.classes.map { it.names[intermediary]!!.value }.toSet())
    }

    @Test
    fun writesLandInDeltaNotBase() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        // a propagated-style write: add a named entry to clsB.methodA
        d.clazz(mapOf(intermediary to InternalName.unchecked("clsB")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))
            .setNames(mapOf(named to UnqualifiedName.unchecked("propagatedB")))
        // merged view shows it
        with(d) {
            assertEquals("propagatedB", cls(d, "clsB")
                .getMethod(intermediary, UnqualifiedName.unchecked("methodA"), null)!!
                .names[named]?.value)
        }
        // the base row is untouched
        assertNull(cls(b, "clsB").methods.first().names[named])
        // and a fresh delta over the same base doesn't see it either
        assertNull(cls(DeltaMappingTree(b), "clsB").methods.first().names[named])
    }

    @Test
    fun deltaOnlyRowsAppearBesideBase() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        d.clazz(mapOf(intermediary to InternalName.unchecked("clsC")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("m;()V")))
        assertEquals(setOf("clsA", "clsB", "clsC"),
            d.classes.map { it.names[intermediary]!!.value }.toSet())
        assertEquals(setOf("clsA", "clsB"),
            b.classes.map { it.names[intermediary]!!.value }.toSet())
    }

    @Test
    fun clazzBindsToSameBaseRow() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        // probing by fns then by named must resolve to one merged class —
        // the delta node is seeded with the base row's full name set, so
        // writes through either handle land in the same delta node
        val byFns = d.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
        val byNamed = d.clazz(mapOf(named to InternalName.unchecked("NamedA")))
        byFns.setNames(mapOf(named to InternalName.unchecked("RenamedA")))
        assertEquals("RenamedA", byNamed.names[named]?.value)
        assertEquals("NamedA", cls(b, "clsA").names[named]?.value)
    }

    @Test
    fun memberIterationDoesNotDuplicateBoundRows() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        // write through the factory — binds a delta member to the base member
        d.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))
            .setNames(mapOf(named to UnqualifiedName.unchecked("aM2")))
        val clsA = cls(d, "clsA")
        val methods = clsA.methods.toList()
        // still exactly one methodA row — bound, not duplicated
        assertEquals(1, methods.count { it.entries[intermediary]?.name?.value == "methodA" })
        assertEquals("aM2", methods.first { it.entries[intermediary]?.name?.value == "methodA" }
            .entries[named]?.name?.value)
        // base still reads the old name
        assertEquals("aM", cls(b, "clsA").methods.first().entries[named]?.name?.value)
    }

    private val IT = """
        umf_it 1 0
        intermediary
        clsA
         public methodA ()V
        clsB clsA
         public methodA ()V
    """.trimIndent()

    /** the real consumer shape: InheritanceBackedMappingTree's only public
     *  constructor takes a *source* tree and re-emits it through propagating
     *  factories into its own private store — so the delta serves as a cheap
     *  read-side view over the shared merged tree. the source is never
     *  written; propagated names land in the backed tree's private rows */
    @Test
    fun inheritanceBackedTreeOverDeltaKeepsBasePristine() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        val it = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        val propagating = InheritanceBackedMappingTree(d, it)

        // write a named member on the parent — propagateDown fills clsB's
        // matching member inside the backed tree's private store
        propagating.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
            .method(mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
                named to MethodNameAndDescriptor.unchecked("aM;()V"),
            ))

        // merged view through the propagating tree shows the propagated name
        assertEquals("aM", propagating.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodA" }
            .entries[named]?.name?.value)
        // the write stayed inside the backed tree — the delta carries nothing
        assertNull(cls(d, "clsB")
            .methods.first { it.entries[intermediary]?.name?.value == "methodA" }
            .entries[named])
        // and the base row is pristine — the whole point
        assertNull(cls(b, "clsB").methods.first().entries[named])
    }

    /** emit-driven propagation through the delta: emitting the merged view
     *  into a backed tree behaves exactly like emitting a copied tree —
     *  propagated names resolve identically */
    @Test
    fun emitThroughDeltaDrivesPropagationIdentically() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        val it = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        val propagating = InheritanceBackedMappingTree(d, it)

        // emit the delta view into the propagating tree — parent row carries
        // the named name; pullDown/propagateDown fill clsB's row
        d.emit(propagating)

        assertEquals("aM", propagating.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodA" }
            .entries[named]?.name?.value)
        assertNull(cls(b, "clsB").methods.first().entries[named])
    }

    /** emit round-trip: a delta tree emits merged rows (base + writes) —
     *  the sink sees a single coherent tree */
    @Test
    fun emitProducesMergedRows() = runTest {
        val b = base()
        val d = DeltaMappingTree(b)
        d.clazz(mapOf(intermediary to InternalName.unchecked("clsB")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))
            .setNames(mapOf(named to UnqualifiedName.unchecked("propagatedB")))
        d.clazz(mapOf(intermediary to InternalName.unchecked("clsC")))

        val out = MutableMappingTree(d.namespaces)
        d.emit(out)
        // clsB emitted with the delta name merged in
        assertEquals("propagatedB", cls(out, "clsB")
            .methods.first().entries[named]?.name?.value)
        // delta-only class emitted too
        assertEquals("clsC", cls(out, "clsC").names[intermediary]?.value)
        // base row count unchanged
        assertEquals(2, b.classes.count())
    }

}
