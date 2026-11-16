package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.bytecodeFill
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.mappingTree
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [bytecodeFill] — materializes the remap-time member-group propagation
 * (LazyASMInheritanceTree + MemberGroups + ResolvingMappingTree) back
 * into a mutable tree:
 *
 * - hierarchy member names propagate (sub/impl/deep rows + scan-only
 *   classes in `extraClasses`);
 * - explicit target cells win over the group winner;
 * - library classes contribute hierarchy edges only — never gain rows;
 * - env-qualified target columns are never filled;
 * - re-running is idempotent.
 */
private fun clsScan(
    name: String,
    superType: String? = "java/lang/Object",
    methods: List<Triple<String, String, Int>> = emptyList(),
    fields: List<Triple<String, String, Int>> = emptyList(),
    interfaces: List<String> = emptyList()
) = InternalName.unchecked(name) to ASMInheritanceTree.RawClassInfo(
    superType?.let(InternalName::unchecked),
    interfaces.map(InternalName::unchecked),
    methods.map { (n, d, a) ->
        ASMInheritanceTree.RawMethodInfo(
            UnqualifiedName.unchecked(n), MethodDescriptor.unchecked(d), a)
    },
    fields.map { (n, d, a) ->
        ASMInheritanceTree.RawFieldInfo(
            UnqualifiedName.unchecked(n), FieldDescriptor.unchecked(d), a)
    }
)

private fun classBytes(name: String, methods: List<MethodNode>): ByteArray {
    val node = ClassNode().apply {
        this.access = Opcodes.ACC_PUBLIC
        this.name = name
        this.superName = "java/lang/Object"
        this.version = Opcodes.V17
        this.methods.addAll(methods)
    }
    val writer = ClassWriter(0)
    node.accept(writer)
    return writer.toByteArray()
}

private fun pubMethod(name: String, desc: String = "()V") =
    MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null).apply {
        visitCode()
        visitInsn(Opcodes.RETURN)
        visitMaxs(0, 1)
        visitEnd()
    }

private fun writeJar(path: Path, entries: Map<String, ByteArray>) {
    ZipOutputStream(Files.newOutputStream(path)).use { jos ->
        for ((name, bytes) in entries) {
            jos.putNextEntry(ZipEntry(name))
            jos.write(bytes)
            jos.closeEntry()
        }
    }
}

class BytecodeFillTest {

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")
    private val clientOnly = Namespace("intermediary:client")

    private class Fixture(val dir: Path) {
        val scan = ASMInheritanceTree.ScanData(
            mapOf(
                clsScan("a/Base", methods = listOf(
                    Triple("m", "()V", Opcodes.ACC_PUBLIC),
                    Triple("e", "()V", Opcodes.ACC_PUBLIC))),
                clsScan("a/Sub", "a/Base", listOf(
                    Triple("m", "()V", Opcodes.ACC_PUBLIC),
                    Triple("e", "()V", Opcodes.ACC_PUBLIC))),
                clsScan("a/Unmapped", "a/Base", listOf(
                    Triple("m", "()V", Opcodes.ACC_PUBLIC),
                    Triple("e", "()V", Opcodes.ACC_PUBLIC))),
                clsScan("a/SubLib", "lib/LibBase", listOf(
                    Triple("l", "()V", Opcodes.ACC_PUBLIC))),
                clsScan("a/Other", methods = listOf(
                    Triple("o", "()V", Opcodes.ACC_PUBLIC))),
            )
        )

        val libJar: Path = dir.resolve("lib.jar").also { jar ->
            writeJar(jar, mapOf(
                "lib/LibBase.class" to classBytes("lib/LibBase",
                    listOf(pubMethod("l"))),
                "lib/Other.class" to classBytes("lib/Other",
                    listOf(pubMethod("o"))),
            ))
        }

        val classpathIndex = LazyASMInheritanceTree.index(setOf(libJar))

        val tree: MutableMappingTree = mappingTree(
            "intermediary", "named", "intermediary:client"
        ) {
            clazz("a/Base", "nb/Base") {
                method("m;()V", "run")
                method("e;()V", "exBase")
            }
            clazz("a/Sub", "nb/Sub") {
                method("m;()V", null)
                // explicit target — the group winner must not overwrite it
                method("e;()V", "explicitE")
            }
            clazz("a/SubLib", "nb/SubLib") {
                method("l;()V", null)
            }
            clazz("a/Other", "nb/Other") {
                method("o;()V", null)
            }
            clazz("lib/LibBase", "nb/LibBase") {
                method("l;()V", "libRun")
            }
        }
    }

    private fun MutableMappingTree.methodName(
        cls: String, name: String, desc: String, ns: Namespace = named
    ): String? = getClass(intermediary, InternalName.unchecked(cls))?.let { c ->
        with(this) {
            c.getMethod(intermediary, UnqualifiedName.unchecked(name),
                MethodDescriptor.unchecked(desc))
        }
    }?.names?.get(ns)?.value

    @Test
    fun `hierarchy propagation materializes member names`() {
        val f = Fixture(createTempDirectory())
        val conflicts = f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), f.classpathIndex)
        // the e-component has {exBase, explicitE} — one real conflict
        assertEquals(1, conflicts)
        // plain override propagation
        assertEquals("run", f.tree.methodName("a/Sub", "m", "()V"))
        // explicit target cells win over the group winner
        assertEquals("explicitE", f.tree.methodName("a/Sub", "e", "()V"))
        // the library supertype seeds names into product subclasses
        assertEquals("libRun", f.tree.methodName("a/SubLib", "l", "()V"))
        // unrelated members resolve nothing
        assertNull(f.tree.methodName("a/Other", "o", "()V"))
    }

    @Test
    fun `extraClasses surface with resolved members`() {
        val f = Fixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), f.classpathIndex)
        val cls = f.tree.getClass(intermediary, InternalName.unchecked("a/Unmapped"))
        assertNotNull(cls)
        assertEquals("run", f.tree.methodName("a/Unmapped", "m", "()V"))
        // component winner lands on the synthesized row — exBase sorts
        // before explicitE
        assertEquals("exBase", f.tree.methodName("a/Unmapped", "e", "()V"))
        // and the row carries its source-namespace identity
        assertEquals("a/Unmapped", cls.names[intermediary]?.value)
    }

    @Test
    fun `library classes never gain rows`() {
        val f = Fixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), f.classpathIndex)
        // lib/Other exists in the jar but is not a product class
        assertNull(f.tree.getClass(intermediary, InternalName.unchecked("lib/Other")))
    }

    @Test
    fun `env-qualified targets are never filled`() {
        val f = Fixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named, clientOnly),
            listOf(f.scan), f.classpathIndex)
        for (cls in f.tree.classes) {
            for (m in cls.methods) {
                assertTrue(m.entries[clientOnly] == null,
                    "env column filled on ${cls.names} $m")
            }
        }
        // and unqualified fills still happened
        assertEquals("run", f.tree.methodName("a/Sub", "m", "()V"))
    }

    /** javac bridge shape: `a/Sub implements i/Cmp` carries the erased
     *  `cmp(Ljava/lang/Object;)I` bridge (synthetic+bridge flags) plus the
     *  specialized `cmp(La/Cmp;)I` it forwards to. Unification merges them
     *  into one component so the specialized row carries the propagated
     *  name too — the ploceus named-namespace behavior. */
    private class BridgeFixture(val dir: Path) {
        val scan = ASMInheritanceTree.ScanData(
            mapOf(
                clsScan("i/Cmp", methods = listOf(
                    Triple("cmp", "(Ljava/lang/Object;)I",
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT))),
                clsScan("a/Cmp"),
                clsScan("a/Sub", methods = listOf(
                    Triple("cmp", "(Ljava/lang/Object;)I",
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE),
                    Triple("cmp", "(La/Cmp;)I", Opcodes.ACC_PUBLIC)),
                    interfaces = listOf("i/Cmp")),
            )
        )

        val tree: MutableMappingTree = mappingTree("intermediary", "named") {
            clazz("i/Cmp", "nb/Cmp") {
                method("cmp;(Ljava/lang/Object;)I", "compare")
            }
            clazz("a/Sub", "nb/Sub") {
                method("cmp;(Ljava/lang/Object;)I", null)
                method("cmp;(La/Cmp;)I", null)
            }
        }
    }

    @Test
    fun `bridge methods unify with specialized targets`() {
        val f = BridgeFixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        // the bridge is a plain override — propagated regardless
        assertEquals("compare", f.tree.methodName("a/Sub", "cmp", "(Ljava/lang/Object;)I"))
        // the specialized target only carries a name via unification
        assertEquals("compare", f.tree.methodName("a/Sub", "cmp", "(La/Cmp;)I"))
    }

    @Test
    fun `unifyBridges=false leaves the specialized row unfilled`() {
        val f = BridgeFixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap(),
            unifyBridges = false)
        assertEquals("compare", f.tree.methodName("a/Sub", "cmp", "(Ljava/lang/Object;)I"))
        assertNull(f.tree.methodName("a/Sub", "cmp", "(La/Cmp;)I"))
    }

    /** obfuscated-jar bridge shape: `a/Cmp extends a/GenBase` (which
     *  declares the erased `compareTo(Ljava/lang/Object;)I`) carries the
     *  `ACC_BRIDGE` method under the *interface signature* name while the
     *  specialized target is renamed `a(La/Cmp;)I` — no shared name, so
     *  only the relaxed narrowing filter unifies them. */
    private class RenamedBridgeFixture {
        val scan = ASMInheritanceTree.ScanData(
            mapOf(
                clsScan("a/GenBase", methods = listOf(
                    Triple("compareTo", "(Ljava/lang/Object;)I",
                        Opcodes.ACC_PUBLIC)),
                    interfaces = listOf("java/lang/Comparable")),
                clsScan("a/Cmp", "a/GenBase", listOf(
                    Triple("compareTo", "(Ljava/lang/Object;)I",
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE),
                    Triple("a", "(La/Cmp;)I", Opcodes.ACC_PUBLIC))),
            )
        )

        val tree: MutableMappingTree = mappingTree("intermediary", "named") {
            clazz("a/GenBase", "nb/GenBase") {
                method("compareTo;(Ljava/lang/Object;)I", "compareTo")
            }
            clazz("a/Cmp", "nb/Cmp") {
                method("compareTo;(Ljava/lang/Object;)I", null)
                method("a;(La/Cmp;)I", null)
            }
        }
    }

    @Test
    fun `renamed specialized target unifies with bridge`() {
        val f = RenamedBridgeFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        assertEquals("compareTo", f.tree.methodName("a/Cmp", "compareTo", "(Ljava/lang/Object;)I"))
        // the renamed target resolves the bridge component's name
        assertEquals("compareTo", f.tree.methodName("a/Cmp", "a", "(La/Cmp;)I"))
    }

    @Test
    fun `renamed target stays unfilled when unifyBridges is off`() {
        val f = RenamedBridgeFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap(),
            unifyBridges = false)
        assertEquals("compareTo", f.tree.methodName("a/Cmp", "compareTo", "(Ljava/lang/Object;)I"))
        assertNull(f.tree.methodName("a/Cmp", "a", "(La/Cmp;)I"))
    }

    /** param-name propagation: `a/P.m(I)V` mapped `run` with param
     *  `arg0`; the unmapped override on `a/C` must gain both the
     *  propagated method name and the donor's param name. */
    private class ParamFixture {
        val scan = ASMInheritanceTree.ScanData(
            mapOf(
                clsScan("a/P", methods = listOf(
                    Triple("m", "(I)V", Opcodes.ACC_PUBLIC))),
                clsScan("a/C", "a/P", listOf(
                    Triple("m", "(I)V", Opcodes.ACC_PUBLIC))),
            )
        )

        val tree: MutableMappingTree = mappingTree("intermediary", "named") {
            clazz("a/P", "nb/P") {
                method("m;(I)V", "run") {
                    parameter(0, 1, "a", "arg0")
                }
            }
            clazz("a/C", "nb/C") {
                method("m;(I)V", null)
            }
        }
    }

    private fun MutableMappingTree.paramName(
        cls: String, name: String, desc: String, lvOrd: Int, ns: Namespace = named
    ): String? = getClass(intermediary, InternalName.unchecked(cls))?.let { c ->
        with(this) {
            c.getMethod(intermediary, UnqualifiedName.unchecked(name),
                MethodDescriptor.unchecked(desc))
        }
    }?.parameters?.firstOrNull { it.lvOrd == lvOrd }?.names?.get(ns)?.value

    @Test
    fun `propagated method names seed param names`() {
        val f = ParamFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        assertEquals("run", f.tree.methodName("a/C", "m", "(I)V"))
        // the donor's named param lands on the override's lvOrd-1 slot
        assertEquals("arg0", f.tree.paramName("a/C", "m", "(I)V", 1))
    }

    /** the composer verbatim-fill scenario: target columns arrive
     *  pre-filled with the source name (`fillMissingNames` shape — every
     *  row carries `named=<fns>`). Those identity cells are not evidence
     *  of a real rename and must yield to the propagated component name;
     *  genuine explicit cells still win. */
    private class IdentityFixture {
        val scan = ASMInheritanceTree.ScanData(
            mapOf(
                clsScan("a/Base", methods = listOf(
                    Triple("m", "(I)V", Opcodes.ACC_PUBLIC),
                    Triple("e", "(I)V", Opcodes.ACC_PUBLIC),
                    Triple("f", "(I)V", Opcodes.ACC_PUBLIC))),
                // identity-filled override — `named=m` on a row sourced `m`
                clsScan("a/SubI", "a/Base", listOf(
                    Triple("m", "(I)V", Opcodes.ACC_PUBLIC))),
                // genuinely-mapped override
                clsScan("a/SubE", "a/Base", listOf(
                    Triple("e", "(I)V", Opcodes.ACC_PUBLIC))),
                // unmapped override
                clsScan("a/SubA", "a/Base", listOf(
                    Triple("f", "(I)V", Opcodes.ACC_PUBLIC))),
            )
        )

        val tree: MutableMappingTree = mappingTree("intermediary", "named") {
            clazz("a/Base", "nb/Base") {
                method("m;(I)V", "run") {
                    parameter(0, 1, "a", "arg0")
                }
                method("e;(I)V", "exec")
                method("f;(I)V", "fire")
            }
            clazz("a/SubI", "nb/SubI") {
                // verbatim cell the composer stamped before the fill
                method("m;(I)V", "m")
            }
            clazz("a/SubE", "nb/SubE") {
                method("e;(I)V", "other")
            }
            clazz("a/SubA", "nb/SubA") {
                method("f;(I)V", null)
            }
        }
    }

    @Test
    fun `identity-filled cells yield to propagation`() {
        val f = IdentityFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        // the verbatim `named=m` cell is overwritten by the component name
        assertEquals("run", f.tree.methodName("a/SubI", "m", "(I)V"))
        // and the overwrite still counts as a gain — donor params land
        assertEquals("arg0", f.tree.paramName("a/SubI", "m", "(I)V", 1))
    }

    @Test
    fun `differing explicit cells keep their name`() {
        val f = IdentityFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        // `other` is a genuine mapping — the component winner `exec`
        // must not overwrite it
        assertEquals("other", f.tree.methodName("a/SubE", "e", "(I)V"))
    }

    @Test
    fun `absent cells still fill`() {
        val f = IdentityFixture()
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), emptyMap())
        assertEquals("fire", f.tree.methodName("a/SubA", "f", "(I)V"))
    }

    @Test
    fun `materializeAll covers the whole index`() {
        val f = Fixture(createTempDirectory())
        val graph = LazyASMInheritanceTree(intermediary, f.scan, f.classpathIndex)
        try {
            val libOther = InternalName.unchecked("lib/Other")
            // keys only enumerate materialized classes while lazy
            assertTrue(libOther !in graph.classes.keys)
            graph.materializeAll()
            assertTrue(libOther in graph.classes.keys)
            assertTrue(InternalName.unchecked("lib/LibBase") in graph.classes.keys)
            assertNotNull(graph.classes[libOther])
        } finally {
            graph.close()
        }
    }

    @Test
    fun `re-running is a no-op`() {
        val f = Fixture(createTempDirectory())
        f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), f.classpathIndex)
        val snapshot = f.tree.classes.flatMap { c ->
            c.methods.flatMap { m -> m.entries.map { (ns, e) ->
                "${c.names[intermediary]} ${e.value} $ns" } }
        }.sorted()
        val second = f.tree.bytecodeFill(
            intermediary, setOf(named), listOf(f.scan), f.classpathIndex)
        val after = f.tree.classes.flatMap { c ->
            c.methods.flatMap { m -> m.entries.map { (ns, e) ->
                "${c.names[intermediary]} ${e.value} $ns" } }
        }.sorted()
        assertEquals(snapshot, after)
        // re-stamping is idempotent — same conflicts reported
        assertEquals(1, second)
    }

}
