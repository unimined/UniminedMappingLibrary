package xyz.wagyourtail.unimined.mapping.test.inheritance

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.SyntheticBridgeElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SyntheticBridgeElementData
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceConflictException
import xyz.wagyourtail.unimined.mapping.inheritance.PropagationStrategy
import xyz.wagyourtail.unimined.mapping.inheritance.propagate
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestInheritanceBackedMappingTree {

    private val IT = """
        umf_it 1 0
        intermediary
        clsA
         public methodA ()V
         public fieldA I

        clsB clsA intfA intfB
         public methodA ()V
         public fieldA I
         public methodC ()V

        intfA
         public methodC ()V

        intfB
         public methodC ()V
        """.trimIndent()

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")
    private val named2 = Namespace("named2")

    private fun setup(strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT): Pair<MutableMappingTree, InheritanceBackedMappingTree> {
        val source = MutableMappingTree()
        val it = CachedInheritanceTree(source, StringCharReader(IT))
        // both handles point at the backed tree — storage is private; writes
        // must go through the propagating factories
        val tree = InheritanceBackedMappingTree(source, it, strategy)
        return tree to tree
    }

    private fun cls(tree: MutableMappingTree, name: String) =
        tree.clazz(mapOf(intermediary to InternalName.unchecked(name)))

    private fun methodEntries(cls: MutableMappingTree, clsName: String, meth: String) =
        cls.getClass(intermediary, InternalName.unchecked(clsName))!!
            .methods.first { it.entries[intermediary]?.name?.value == meth }
            .entries.mapValues { it.value.name.value }

    @Test
    fun propagatesOnWrite() = runTest {
        val (delegate, tree) = setup()
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        // propagateDown created clsB.methodA with the propagated name
        assertEquals("aM", methodEntries(delegate, "clsB", "methodA")[named])
    }

    @Test
    fun explicitWins() = runTest {
        val (delegate, tree) = setup()
        // child explicit first
        cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        assertEquals("bM", methodEntries(delegate, "clsB", "methodA")[named])
    }

    @Test
    fun pullDownFillsMissingNs() = runTest {
        val (delegate, tree) = setup()
        // parent named in two namespaces first
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V"),
            named2 to MethodNameAndDescriptor.unchecked("aM2;()V")
        ))
        // child attaches later with only one ns — pulls the rest from clsA
        cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        val entries = methodEntries(delegate, "clsB", "methodA")
        assertEquals("bM", entries[named])
        assertEquals("aM2", entries[named2])
    }

    @Test
    fun strictThrowsOnDivergentParents() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.STRICT)
        cls(tree, "intfA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iA;()V")
        ))
        // intfB propagates a different name to clsB.methodC — disagreement
        assertFailsWith<InheritanceConflictException> {
            cls(tree, "intfB").method(mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
                named to MethodNameAndDescriptor.unchecked("iB;()V")
            ))
        }
    }

    @Test
    fun firstInheritedWinsOnDivergentParents() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.FIRST_INHERITED_WINS)
        cls(tree, "intfA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iA;()V")
        ))
        cls(tree, "intfB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iB;()V")
        ))
        assertEquals("iA", methodEntries(delegate, "clsB", "methodC")[named])
    }

    @Test
    fun bridgeSynthesizedOnDivergentParents() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.BRIDGE)
        cls(tree, "intfA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iA;()V")
        ))
        cls(tree, "intfB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iB;()V")
        ))
        // iA stays the propagated name; iB becomes a synthetic bridge on clsB.methodC
        val method = delegate.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodC" }
        assertEquals("iA", method.entries[named]?.name?.value)
        val bridges = method.metadata.filterIsInstance<SyntheticBridgeElement>()
        assertEquals(1, bridges.size)
        assertEquals("iB", bridges[0].names[named]?.value)
    }

    @Test
    fun lastWinsOverwritesExplicit() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.LAST_WINS)
        cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        assertEquals("aM", methodEntries(delegate, "clsB", "methodA")[named])
    }

    @Test
    fun propagatesFieldOnWrite() = runTest {
        val (delegate, tree) = setup()
        cls(tree, "clsA").field(mapOf(
            intermediary to FieldNameAndDescriptor.unchecked("fieldA;I"),
            named to FieldNameAndDescriptor.unchecked("aF;I")
        ))
        val field = delegate.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .fields.first { it.entries[intermediary]?.name?.value == "fieldA" }
        assertEquals("aF", field.entries[named]?.name?.value)
    }

    @Test
    fun explicitViaSetNames() = runTest {
        val (delegate, tree) = setup()
        // attach with fns only, then setNames — the wrapper member marks explicit
        val m = cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")
        ))
        m.setNames(mapOf(named to UnqualifiedName.unchecked("bM")))
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        assertEquals("bM", methodEntries(delegate, "clsB", "methodA")[named])
    }

    @Test
    fun bridgeOnAttachExplicitOverride() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.BRIDGE)
        // parent named first
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        // child attaches with its own explicit name — parent's becomes a bridge
        cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        val method = delegate.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodA" }
        assertEquals("bM", method.entries[named]?.name?.value)
        val bridges = method.metadata.filterIsInstance<SyntheticBridgeElement>()
        assertEquals(1, bridges.size)
        assertEquals("aM", bridges[0].names[named]?.value)
    }

    @Test
    fun bridgeRemovedWhenNameWritten() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.BRIDGE)
        cls(tree, "intfA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iA;()V")
        ))
        cls(tree, "intfB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iB;()V")
        ))
        val method = delegate.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodC" }
        assertEquals(1, method.metadata.filterIsInstance<SyntheticBridgeElement>().size)
        // explicit rename to the bridge's name satisfies it — bridge is dropped
        cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V")
        )).setNames(mapOf(named to UnqualifiedName.unchecked("iB")))
        assertEquals(0, method.metadata.filterIsInstance<SyntheticBridgeElement>().size)
    }

    @Test
    fun bridgeDeduped() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.BRIDGE)
        cls(tree, "intfA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
            named to MethodNameAndDescriptor.unchecked("iA;()V")
        ))
        // same propagated name twice — only one bridge
        repeat(2) {
            cls(tree, "intfB").method(mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("methodC;()V"),
                named to MethodNameAndDescriptor.unchecked("iB;()V")
            ))
        }
        val method = delegate.getClass(intermediary, InternalName.unchecked("clsB"))!!
            .methods.first { it.entries[intermediary]?.name?.value == "methodC" }
        assertEquals(1, method.metadata.filterIsInstance<SyntheticBridgeElement>().size)
    }

    @Test
    fun cleanupBridgesDropsSatisfied() = runTest {
        val (delegate, tree) = setup(PropagationStrategy.BRIDGE)
        val method = cls(tree, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        // manually attach a bridge the member already satisfies
        method.addMetadata(SyntheticBridgeElementData(
            mutableMapOf(named to UnqualifiedName.unchecked("bM"))))
        assertEquals(1, method.metadata.filterIsInstance<SyntheticBridgeElement>().size)
        tree.cleanupBridges()
        assertEquals(0, method.metadata.filterIsInstance<SyntheticBridgeElement>().size)
    }

    @Test
    fun batchPropagateFillsBulkLoaded() = runTest {
        val (delegate, tree) = setup()
        // parent + child bulk-attached first, then propagate(targets) as the fallback
        cls(delegate, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        cls(delegate, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")
        ))
        // the batch path is still the escape hatch for rows bulk-written before wrap
        tree.inheritance.propagate(delegate, setOf(named))
        assertEquals("aM", methodEntries(delegate, "clsB", "methodA")[named])
    }


    /** emit-driven propagation: a tree emitted into an already-constructed
     *  backed tree must propagate via the pop-time re-drive — and a declared
     *  AccessElement on a pushed member row must fold into the scanned flags
     *  before the propagation decision (streamed-AT ordering). */
    @Test
    fun propagateOnEmit() = runTest {
        val (delegate, tree) = setup()

        // a separate source tree — the streamed-read analog
        val src = MutableMappingTree(setOf(intermediary, named))
        val clsA = src.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
        clsA.method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        src.clazz(mapOf(intermediary to InternalName.unchecked("clsB")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))

        src.emit(tree)

        assertEquals("aM", methodEntries(delegate, "clsB", "methodA")[named])
    }


    /** streamed AT fold: a private parent member widened by AccessElements on
     *  its row must propagate once emit pushes the row — the pop-time fold
     *  applies the flags to the scanned graph before propagateDown decides. */
    @Test
    fun emitFoldsAccessElementsBeforePropagation() = runTest {
        val it2 = CachedInheritanceTree(MutableMappingTree(), StringCharReader("""
            umf_it 1 0
            intermediary
            clsA
             private methodP ()V
            clsB clsA
             private methodP ()V
        """.trimIndent()))
        val tree = InheritanceBackedMappingTree(MutableMappingTree(setOf(intermediary, named)), it2)

        val src = MutableMappingTree(setOf(intermediary, named))
        val clsA = src.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
        val m = clsA.method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodP;()V"),
            named to MethodNameAndDescriptor.unchecked("aP;()V")
        )) as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
        m.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.REMOVE,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PRIVATE,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))
        m.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.ADD,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PROTECTED,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))
        src.clazz(mapOf(intermediary to InternalName.unchecked("clsB")))
            .method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodP;()V")))

        src.emit(tree)

        assertEquals("aP", methodEntries(tree, "clsB", "methodP")[named],
            "widened private parent should propagate its name to the child override")
    }


    /** shadowing direction: a child row that exists *before* the parent's row
     *  still gets the name when the parent member attaches (propagateDown),
     *  and an explicit child name survives later parent propagation. */
    @Test
    fun childRowBeforeParentAndExplicitSurvives() = runTest {
        val (delegate, tree) = setup()

        // child row first — no parent row exists yet
        cls(tree, "clsB").method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))
        // parent attaches after — propagateDown must reach the existing child row
        cls(tree, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        assertEquals("aM", methodEntries(delegate, "clsB", "methodA")[named])

        // explicit child name survives a later parent write
        val (delegate2, tree2) = setup()
        cls(tree2, "clsB").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("bM;()V")
        ))
        cls(tree2, "clsA").method(mapOf(
            intermediary to MethodNameAndDescriptor.unchecked("methodA;()V"),
            named to MethodNameAndDescriptor.unchecked("aM;()V")
        ))
        assertEquals("bM", methodEntries(delegate2, "clsB", "methodA")[named])
    }

}
