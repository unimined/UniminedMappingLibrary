package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Inheritance-driven remapping across jars: a mod jar (unmapped) extends a
 * mapped class from a library jar. Propagation over the combined class graph
 * must carry member names down to the child's overrides — and the member's
 * `AccessElement`s decide whether it propagates at all:
 *  - `private` methods are not inherited → a child member of the same shape is
 *    an unrelated method and must NOT be renamed.
 *  - widened `private → protected` → it IS inherited → the child's override
 *    gets the obfuscated name back on the reverse remap.
 */
class InheritanceRemapTest {

    /** Named-side MC jar: Example.mappedMethod exists as private (pre-widened state). */
    private fun mcJar() = mapOf(
        "net/minecraft/Example.class" to classBytes("net/minecraft/Example") {
            methods.add(MethodNode(Opcodes.ACC_PRIVATE, "mappedMethod", "()V", null, null).apply {
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        },
    )

    /** Mod jar: pkg/Mod extends Example and has a same-signature mappedMethod. */
    private fun modJar() = mapOf(
        "pkg/Mod.class" to classBytes("pkg/Mod") {
            superName = "net/minecraft/Example"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "mappedMethod", "()V", null, null).apply {
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        },
    )

    /** tree + a handle to the method row so callers can add metadata */
    private fun baseTree(): Pair<xyz.wagyourtail.unimined.mapping.MutableMappingTree, MutableMethodMapping> {
        val t = treeOf()
        val mm = t.cls("eqm", "net/minecraft/Example")
            .method("method_1;()V", "mappedMethod;()V") as MutableMethodMapping
        return t to mm
    }

    @Test
    fun widenedMethodPropagatesToUnmappedChild() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val mod = dir.resolve("mod.jar"); val out = dir.resolve("out.jar")

        val (tree, mm) = baseTree()
        mm.addMetadata(AccessElement(AddRemove.REMOVE, AccessFlag.PRIVATE, AccessConditions.ALL))
        mm.addMetadata(AccessElement(AddRemove.ADD, AccessFlag.PROTECTED, AccessConditions.ALL))

        writeJar(mc, mcJar())
        writeJar(mod, modJar())

        AsmRemapper.remap(mod, out, listOf(tree), named, obf, libraries = setOf(mc))

        val modOut = readClasses(out).getValue("pkg/Mod")
        assertEquals("eqm", modOut.superName, "supername should remap even though Mod itself is unmapped")
        assertNotNull(
            modOut.methods.firstOrNull { it.name == "method_1" },
            "widened override in the unmapped child should get the obfuscated name: ${modOut.methods.map { it.name }}",
        )
    }

    @Test
    fun stillPrivateMethodDoesNotPropagateToChild() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val mod = dir.resolve("mod.jar"); val out = dir.resolve("out.jar")

        // same setup, no AccessElement — parent's mappedMethod stays private
        writeJar(mc, mcJar())
        writeJar(mod, modJar())

        AsmRemapper.remap(mod, out, listOf(baseTree().first), named, obf, libraries = setOf(mc))

        val modOut = readClasses(out).getValue("pkg/Mod")
        assertEquals(
            listOf("mappedMethod"), modOut.methods.map { it.name },
            "a private (non-inherited) member must not push its name into the child",
        )
    }

    @Test
    fun paramNamesPropagateFromOriginatingMethod() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val mod = dir.resolve("mod.jar"); val out = dir.resolve("out.jar")

        // Mod is mapped but its override has no row — the name (and the
        // param names) must resolve through the originating Example row
        val t = treeOf()
        t.cls("cob", "pkg/Mod")
        val m = t.cls("eqm", "net/minecraft/Example")
            .method("method_1;(I)V", "mappedMethod;(I)V") as MutableMethodMapping
        m.parameter(0, 1, mapOf(
            named to UnqualifiedName.unchecked("value"),
            obf to UnqualifiedName.unchecked("a"),
        ))

        writeJar(mc, mapOf(
            "net/minecraft/Example.class" to classBytes("net/minecraft/Example") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "mappedMethod", "(I)V", null, null).apply {
                    parameters = mutableListOf(ParameterNode("value", 0))
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 2); visitEnd()
                })
            },
        ))
        writeJar(mod, mapOf(
            "pkg/Mod.class" to classBytes("pkg/Mod") {
                superName = "net/minecraft/Example"
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "mappedMethod", "(I)V", null, null).apply {
                    parameters = mutableListOf(ParameterNode("inputArg", 0))
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 2); visitEnd()
                })
            },
        ))

        AsmRemapper.remap(mod, out, listOf(t), named, obf, libraries = setOf(mc))

        val method = readClasses(out).getValue("cob").methods.single { it.name == "method_1" }
        assertEquals("a", method.parameters?.single()?.name,
            "the unmapped override's param name comes from the originating row")
    }

    @Test
    fun interfaceOverridesOnUnmappedImplPropagate() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        // argo shape (b1.7.3 biny): obf interface `ii` carries both rows —
        // `a(Lnn;)V`→addNode and `a(Lff;)V`→addField — while obf impl `cc`
        // (the anonymous JsonListenerToJdomAdapter$2 analog) declares both
        // `a` overloads but has only a class row. Both overrides must
        // inherit the interface's mapped names; an unmapped impl left as
        // `a` produces AbstractMethodError at the callsite.
        val t = treeOf()
        val iface = t.cls("ii", "pkg/I")
        iface.method("a;(Lnn;)V", "addNode;(Lpkg/NodeBuilder;)V")
        iface.method("a;(Lff;)V", "addField;(Lpkg/FieldBuilder;)V")
        t.cls("nn", "pkg/NodeBuilder")
        t.cls("ff", "pkg/FieldBuilder")
        t.cls("cc", "pkg/C")

        writeJar(mc, mapOf(
            "ii.class" to classBytes("ii", Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "a", "(Lnn;)V", null, null))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "a", "(Lff;)V", null, null))
            },
            "nn.class" to classBytes("nn"),
            "ff.class" to classBytes("ff"),
            "cc.class" to classBytes("cc") {
                interfaces.add("ii")
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "a", "(Lnn;)V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 2); visitEnd()
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "a", "(Lff;)V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 2); visitEnd()
                })
            },
        ))

        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/C")
        assertEquals(
            listOf("addField" to "(Lpkg/FieldBuilder;)V", "addNode" to "(Lpkg/NodeBuilder;)V"),
            c.methods.map { it.name to it.desc }.sortedBy { it.second },
            "interface overrides on an unmapped impl must inherit the interface's mapped names",
        )
    }

}
