package xyz.wagyourtail.unimined.mapping.test.tree

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.HasInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.ResolvingMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Parity of [ResolvingMappingTree]'s lazy member synthesis against
 * [InheritanceBackedMappingTree]'s materialized propagation — the two
 * surfaces the `modArtifacts`-style extension passes see must agree:
 * propagated member rows exist exactly on declaring classes, carry the
 * propagated name, and nothing mutates the shared merged tree.
 */
class ResolvingMappingTreeTest {

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")

    /** clsA (mapped method+field) ← clsB (declares both, named only in fns)
     *  ← clsC (declares nothing — pure inherit). */
    private val IT = """
        umf_it 1 0
        intermediary
        clsA
         public fieldA I
         public methodA ()V
        clsB clsA
         public fieldA I
         public methodA ()V
        clsC clsB
        modCls clsA
         public methodA ()V
    """.trimIndent()

    private fun merged(): MutableMappingTree {
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
        clsB.field(mapOf(
            intermediary to FieldNameAndDescriptor.unchecked("fieldA;I"),
        ))
        // clsC has a class row but no member rows
        tree.clazz(mapOf(
            intermediary to InternalName.unchecked("clsC"),
            named to InternalName.unchecked("NamedC"),
        ))
        return tree
    }

    private fun MethodMapping.methodNamed(ns: Namespace) =
        names[ns]?.value
    private fun FieldMapping.fieldNamed(ns: Namespace) =
        names[ns]?.value

    private fun methodOf(tree: xyz.wagyourtail.unimined.mapping.MappingTree,
                       cls: String, name: String = "methodA", desc: String = "()V") =
        tree.getClass(intermediary, InternalName.unchecked(cls))?.let { c ->
            with(tree) {
                c.getMethod(intermediary, UnqualifiedName.unchecked(name),
                    MethodDescriptor.unchecked(desc))
            }
        }

    private fun fieldOf(tree: xyz.wagyourtail.unimined.mapping.MappingTree,
                        cls: String, name: String = "fieldA", desc: String = "I") =
        tree.getClass(intermediary, InternalName.unchecked(cls))?.let { c ->
            with(tree) {
                c.getField(intermediary, UnqualifiedName.unchecked(name),
                    xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor.unchecked(desc))
            }
        }

    /** the materializing baseline — what extension consumers saw before */
    private fun ibt(m: MutableMappingTree) =
        InheritanceBackedMappingTree(m, CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT)))

    /** the lazy adapter under test — same semantics, no materialization */
    private fun resolving(m: MutableMappingTree, extras: Set<String> = emptySet()): ResolvingMappingTree {
        val graph = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        val groups = MemberGroups(graph)
        groups.propagate(m)
        return ResolvingMappingTree(m, graph, groups, intermediary,
            extraClasses = extras.map { InternalName.unchecked(it) }.toSet())
    }

    @Test
    fun propagatedMethodRowExistsOnDeclaringChild() = runTest {
        val m = merged()
        // baseline: IBT materializes clsB.methodA with the propagated name
        val viaIbt = methodOf(ibt(m), "clsB")!!
        assertEquals("aM", viaIbt.methodNamed(named))
        // adapter: the row resolves the same name without materializing
        val r = resolving(m)
        val viaResolving = methodOf(r, "clsB")
        assertNotNull(viaResolving)
        assertEquals("aM", viaResolving.methodNamed(named))
        // and the shared merged tree still shows no named entry — nothing
        // was materialized into it
        assertNull(methodOf(m, "clsB")!!.names[named])
    }

    @Test
    fun fieldOnDeclaringChildKeepsItsOwnCell() = runTest {
        val m = merged()
        // IBT's down-propagation fills the declared child's cell with the
        // ancestor's name (aF) — but the graph remap (what produced the
        // target jar) resolves fields by declaring-chain pull and leaves
        // clsB.fieldA unmapped, so IBT's answer pointed at a name the
        // output jar never carried. The adapter follows the graph-remap
        // answer — the consistent one — and this is the one documented
        // semantic divergence from IBT.
        assertEquals("aF", fieldOf(ibt(m), "clsB")!!.fieldNamed(named))
        val r = resolving(m)
        val row = fieldOf(r, "clsB")
        assertNotNull(row)
        assertNull(row.fieldNamed(named))
        // same answer through the namespaced remapper
        assertEquals("fieldA", r.remapper(intermediary, named).mapField(
            InternalName.unchecked("clsB"),
            UnqualifiedName.unchecked("fieldA"),
            xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor.unchecked("I")
        ).value)
        assertNull(fieldOf(m, "clsB")!!.names[named])
    }

    @Test
    fun noRowOnNonDeclaringDescendant() = runTest {
        val m = merged()
        // IBT never materializes a row where the class doesn't declare the
        // member — adapter must agree, or `memberCandidateCount`-style
        // consumers diverge
        assertNull(methodOf(ibt(m), "clsC"))
        assertNull(methodOf(resolving(m), "clsC"))
        assertNull(fieldOf(ibt(m), "clsC"))
        assertNull(fieldOf(resolving(m), "clsC"))
    }

    @Test
    fun explicitRowsPassThroughUntouched() = runTest {
        val m = merged()
        val r = resolving(m)
        assertEquals("aM", methodOf(r, "clsA")!!.methodNamed(named))
        assertEquals("aF", fieldOf(r, "clsA")!!.fieldNamed(named))
        assertEquals("NamedA", r.getClass(intermediary, InternalName.unchecked("clsA"))!!
            .names[named]?.value)
    }

    @Test
    fun memberIterationMatchesIbtForMethods() = runTest {
        val m = merged()
        val ibtClsB = ibt(m).getClass(intermediary, InternalName.unchecked("clsB"))!!
        val rClsB = resolving(m).getClass(intermediary, InternalName.unchecked("clsB"))!!
        // methods agree with IBT (component names == propagated names);
        // fields diverge intentionally — see fieldOnDeclaringChildKeepsItsOwnCell
        fun msig(c: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping) =
            c.methods.map { it.entries[intermediary]?.name?.value to it.names[named]?.value }.sortedBy { it.first }
        assertEquals(msig(ibtClsB), msig(rClsB))
        fun fsig(c: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping) =
            c.fields.map { it.entries[intermediary]?.name?.value to it.names[named]?.value }.sortedBy { it.first }
        assertEquals(listOf("fieldA" to null), fsig(rClsB))
    }

    @Test
    fun scanOnlyClassesSurfaceViaExtraClasses() = runTest {
        val m = merged()
        // modCls exists only in the scan — IBT upserts it when propagation
        // lands; the adapter surfaces it via extraClasses
        val ibtTree = ibt(m)
        assertEquals("aM", methodOf(ibtTree, "modCls")!!.methodNamed(named))
        val r = resolving(m, extras = setOf("modCls"))
        val modCls = r.getClass(intermediary, InternalName.unchecked("modCls"))
        assertNotNull(modCls)
        with(r) {
            assertEquals("aM", modCls.getMethod(intermediary,
                UnqualifiedName.unchecked("methodA"), MethodDescriptor.unchecked("()V"))!!
                .names[named]?.value)
        }
        // and it shows up in class iteration (it has a synthesized member)
        assertTrue(r.classes.any { it.names[intermediary]?.value == "modCls" })
    }

    @Test
    fun remapperIsGraphBackedForSourceLookups() = runTest {
        val m = merged()
        val r = resolving(m)
        // fns -> named: propagated answer (clsB.methodA -> aM), not flat
        // (flat would return methodA — clsB's own row is fns-only)
        val fwd = r.remapper(intermediary, named)
        assertEquals("aM", fwd.mapMethod(
            InternalName.unchecked("clsB"),
            UnqualifiedName.unchecked("methodA"),
            MethodDescriptor.unchecked("()V")).value)
        // class names still flow through the delegate
        assertEquals("NamedB", fwd.mapClass(InternalName.unchecked("clsB")).value)
        // non-source `from` falls back to flat row lookups
        val rev = r.remapper(named, intermediary)
        assertEquals("clsB", rev.mapClass(InternalName.unchecked("NamedB")).value)
    }

    @Test
    fun sharedGraphAndDelegateStayPristine() = runTest {
        val m = merged()
        val graph = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        val groups = MemberGroups(graph)
        groups.propagate(m)
        val r = ResolvingMappingTree(m, graph, groups, intermediary)
        // exercise lookups — nothing may mutate the merged delegate
        methodOf(r, "clsB"); fieldOf(r, "clsB")
        r.classes.toList()
        assertNull(methodOf(m, "clsB")!!.names[named])
        assertNull(fieldOf(m, "clsB")!!.names[named])
        assertEquals(3, m.classes.count())
    }

    @Test
    fun exposesInheritanceTreeForScanLookups() = runTest {
        val m = merged()
        val graph = CachedInheritanceTree(MutableMappingTree(), StringCharReader(IT))
        val groups = MemberGroups(graph)
        groups.propagate(m)
        val r = ResolvingMappingTree(m, graph, groups, intermediary)
        assertSame(graph, (r as HasInheritanceTree).inheritance)
    }

    @Test
    fun emitRoundTripsSynthesizedRows() = runTest {
        val m = merged()
        val r = resolving(m)
        // emitting through the view materializes the same propagated rows
        // an IBT sink would carry — sinks see a coherent tree
        val out = MutableMappingTree(r.namespaces)
        r.emit(out)
        assertEquals("aM", methodOf(out, "clsB")!!.names[named]?.value)
        // fields keep the declaring row's cell — see
        // fieldOnDeclaringChildKeepsItsOwnCell for the IBT divergence
        assertNull(fieldOf(out, "clsB")!!.names[named])
    }

}
