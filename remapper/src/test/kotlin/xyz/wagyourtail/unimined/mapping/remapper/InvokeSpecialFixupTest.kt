package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * invokespecial→invokevirtual normalization ([xyz.wagyourtail.unimined.mapping.remapper.asm.JarFixups.normalizeInvokeSpecial]):
 * a same-class `invokespecial` whose target is *non-private in the output*
 * only exists when a transform (access widening) created the inconsistency —
 * javac emits that shape exclusively for private methods, so the
 * javac-consistent output form is virtual dispatch. Super-calls,
 * constructors, still-private targets and inherited (non-declared) targets
 * must never rewrite.
 */
class InvokeSpecialFixupTest {

    private val lambdaBsm = Handle(
        Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false,
    )

    /** `private void m_1() {}` */
    private fun privateTarget(name: String = "m_1") =
        MethodNode(Opcodes.ACC_PRIVATE, name, "()V", null, null).apply {
            visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
        }

    /** `void caller() { this.m_1(); }` as pre-nestmate javac emits it for a private target */
    private fun selfInvokeSpecialCaller(owner: String, target: String = "m_1", opcode: Int = Opcodes.INVOKESPECIAL, access: Int = Opcodes.ACC_PUBLIC, itf: Boolean = false) =
        MethodNode(access, "caller", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(opcode, owner, target, "()V", itf)
            visitInsn(Opcodes.RETURN)
            visitMaxs(1, 1)
            visitEnd()
        }

    /** obf `eqm` → named `net/minecraft/Example`, `m_1` → `doThing`; row handle for metadata */
    private fun baseTree(): Pair<MutableMappingTree, MutableMethodMapping> {
        val t = treeOf()
        val mm = t.cls("eqm", "net/minecraft/Example")
            .method("m_1;()V", "doThing;()V") as MutableMethodMapping
        return t to mm
    }

    private fun MutableMethodMapping.widen() {
        addMetadata(AccessElement(AddRemove.REMOVE, AccessFlag.PRIVATE, AccessConditions.ALL))
        addMetadata(AccessElement(AddRemove.ADD, AccessFlag.PUBLIC, AccessConditions.ALL))
    }

    private fun invokeInsns(node: MethodNode) =
        node.instructions.toArray().filterIsInstance<MethodInsnNode>()

    // (a) widened private same-class invokespecial → rewritten to invokevirtual
    @Test
    fun widenedPrivateSelfCallIsRewrittenToVirtual() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val (tree, mm) = baseTree()
        mm.widen()

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(privateTarget())
                methods.add(selfInvokeSpecialCaller("eqm"))
            },
        ))

        // streaming path (all-perClass default extensions)
        AsmRemapper.remap(input, output, tree, obf, named)
        assertSelfCallRewritten(output, "streaming")

        // fused path (fixInnerClasses forces the buffered pipeline)
        val fused = dir.resolve("out-fused.jar")
        AsmRemapper.remap(input, fused, tree, obf, named, Options(fixInnerClasses = true))
        assertSelfCallRewritten(fused, "fused")
    }

    private fun assertSelfCallRewritten(output: java.nio.file.Path, label: String) {
        val out = readClasses(output).getValue("net/minecraft/Example")
        val target = out.methods.single { it.name == "doThing" }
        assertTrue(target.access and Opcodes.ACC_PUBLIC != 0, "$label: widening must have applied before the fixup reads access")
        assertTrue(target.access and Opcodes.ACC_PRIVATE == 0)
        val call = invokeInsns(out.methods.single { it.name == "caller" }).single { it.name == "doThing" }
        assertEquals(Opcodes.INVOKEVIRTUAL, call.opcode, "$label: widened same-class invokespecial must become invokevirtual")
        assertFalse(call.itf, "$label: class owner → itf must be false")
    }

    // (b) still-private target → untouched
    @Test
    fun stillPrivateSelfCallIsUntouched() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val (tree, _) = baseTree() // rename only, no widening

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(privateTarget())
                methods.add(selfInvokeSpecialCaller("eqm"))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        val call = invokeInsns(out.methods.single { it.name == "caller" }).single { it.name == "doThing" }
        assertEquals(Opcodes.INVOKESPECIAL, call.opcode, "still-private target must stay invokespecial")
    }

    // (c) super-call to a widened method → untouched (owner precision — forge's
    // name+desc-only matching would rewrite this and corrupt deliberate
    // non-virtual dispatch)
    @Test
    fun superCallToWidenedMethodIsUntouched() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val mm = tree.cls("pa", "pkg/Parent").method("m_1;()V", "doThing;()V") as MutableMethodMapping
        mm.widen()
        tree.cls("cb", "pkg/Child")

        writeJar(input, mapOf(
            "pa.class" to classBytes("pa") {
                methods.add(privateTarget())
            },
            "cb.class" to classBytes("cb") {
                superName = "pa"
                // super.doThing() shape — owner is the parent, not cb
                methods.add(selfInvokeSpecialCaller("pa"))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val parent = readClasses(output).getValue("pkg/Parent")
        assertTrue(parent.methods.single { it.name == "doThing" }.access and Opcodes.ACC_PUBLIC != 0,
            "control: the parent method really was widened")
        val child = readClasses(output).getValue("pkg/Child")
        val call = invokeInsns(child.methods.single { it.name == "caller" }).single { it.name == "doThing" }
        assertEquals(Opcodes.INVOKESPECIAL, call.opcode, "super-calls must never rewrite — deliberate non-virtual dispatch")
        assertEquals("pkg/Parent", call.owner)
    }

    // (d) constructor → untouched even when widened (ATs widen ctors routinely)
    @Test
    fun constructorCallIsUntouched() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val mm = tree.cls("eqm", "net/minecraft/Example")
            .method("<init>;()V", "<init>;()V") as MutableMethodMapping
        mm.widen()

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(MethodNode(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                })
                // this()-chaining ctor — same-class invokespecial to <init>
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "eqm", "<init>", "()V", false)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 2)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        val chaining = out.methods.single { it.name == "<init>" && it.desc == "(I)V" }
        val call = invokeInsns(chaining).single { it.owner == "net/minecraft/Example" }
        assertEquals(Opcodes.INVOKESPECIAL, call.opcode, "constructors never virtualize")
        assertEquals("<init>", call.name)
    }

    // (e) invokedynamic bootstrap handles: H_INVOKESPECIAL → H_INVOKEVIRTUAL
    // under the same predicate; still-private handles untouched
    @Test
    fun indyBootstrapHandlesFollowTheSamePredicate() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        (cls.method("m_1;()V", "doThing;()V") as MutableMethodMapping).widen()
        cls.method("m_2;()V", "stillPrivate;()V") // no widening — control

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(privateTarget("m_1"))
                methods.add(privateTarget("m_2"))
                methods.add(MethodNode(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                    "lambda\$make\$0", "()V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                // `Runnable r = this::m_1 / this::m_2` shape — impl handles in bsmArgs
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "make", "()V", null, null).apply {
                    visitCode()
                    instructions.add(InvokeDynamicInsnNode(
                        "run", "()Ljava/lang/Runnable;", lambdaBsm,
                        Type.getType("()V"),
                        Handle(Opcodes.H_INVOKESPECIAL, "eqm", "m_1", "()V", false),
                        Type.getType("()V"),
                    ))
                    instructions.add(InvokeDynamicInsnNode(
                        "run", "()Ljava/lang/Runnable;", lambdaBsm,
                        Type.getType("()V"),
                        Handle(Opcodes.H_INVOKESPECIAL, "eqm", "m_2", "()V", false),
                        Type.getType("()V"),
                    ))
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                })
                // ldc of a method-handle constant — same predicate applies
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "handleConst", "()V", null, null).apply {
                    visitCode()
                    visitLdcInsn(Handle(Opcodes.H_INVOKESPECIAL, "eqm", "m_1", "()V", false))
                    visitInsn(Opcodes.POP)
                    visitLdcInsn(Handle(Opcodes.H_INVOKESPECIAL, "eqm", "m_2", "()V", false))
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        val indys = out.methods.single { it.name == "make" }.instructions.toArray()
            .filterIsInstance<InvokeDynamicInsnNode>()
        val widened = indys[0].bsmArgs[1] as Handle
        assertEquals("doThing", widened.name)
        assertEquals(Opcodes.H_INVOKEVIRTUAL, widened.tag,
            "widened impl-method handle must become H_INVOKEVIRTUAL")
        assertFalse(widened.isInterface)
        val control = indys[1].bsmArgs[1] as Handle
        assertEquals("stillPrivate", control.name)
        assertEquals(Opcodes.H_INVOKESPECIAL, control.tag,
            "still-private impl-method handle must stay H_INVOKESPECIAL")

        val ldcs = out.methods.single { it.name == "handleConst" }.instructions.toArray()
            .filterIsInstance<org.objectweb.asm.tree.LdcInsnNode>().map { it.cst as Handle }
        assertEquals(Opcodes.H_INVOKEVIRTUAL, ldcs[0].tag, "ldc handle constants follow the same predicate")
        assertEquals(Opcodes.H_INVOKESPECIAL, ldcs[1].tag, "still-private ldc handle stays")
    }

    // (f) interface host: private interface method widened → INVOKEINTERFACE /
    // H_INVOKEINTERFACE (invokevirtual is not the javac-consistent interface form)
    @Test
    fun interfaceHostRewritesToInvokeInterface() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val mm = tree.cls("ifce", "pkg/Iface").method("m_1;()V", "doThing;()V") as MutableMethodMapping
        mm.widen()

        writeJar(input, mapOf(
            "ifce.class" to classBytes("ifce", Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) {
                methods.add(privateTarget())
                // java-9+ style: a default method calling the interface's own private method
                methods.add(selfInvokeSpecialCaller("ifce", itf = true))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("pkg/Iface")
        val call = invokeInsns(out.methods.single { it.name == "caller" }).single { it.name == "doThing" }
        assertEquals(Opcodes.INVOKEINTERFACE, call.opcode,
            "interface hosts must rewrite to invokeinterface, not invokevirtual")
        assertTrue(call.itf, "invokeinterface requires itf=true")
    }

    // (g) owner==self but the method is NOT declared in the class — resolution
    // would walk up to the inherited super method, whose dispatch is
    // deliberately non-virtual; rewriting would corrupt it
    @Test
    fun inheritedResolutionIsUntouched() = kotlinx.coroutines.test.runTest {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val mm = tree.cls("pa", "pkg/Parent").method("m_1;()V", "doThing;()V") as MutableMethodMapping
        mm.widen()
        tree.cls("cb", "pkg/Child")

        writeJar(input, mapOf(
            "pa.class" to classBytes("pa") {
                methods.add(privateTarget())
            },
            "cb.class" to classBytes("cb") {
                superName = "pa"
                // does NOT declare m_1 — yet the insn owner is cb itself:
                // resolution walks up to pa's method → non-virtual super semantics
                methods.add(selfInvokeSpecialCaller("cb"))
            },
        ))

        // graph entry point so the inherited ref resolves through the hierarchy
        AsmRemapper.remap(input, output, listOf(tree), obf, named)

        val child = readClasses(output).getValue("pkg/Child")
        val call = invokeInsns(child.methods.single { it.name == "caller" }).single()
        assertEquals("doThing", call.name, "control: the inherited ref really resolves to the widened row")
        assertEquals(Opcodes.INVOKESPECIAL, call.opcode,
            "inherited-resolution invokespecial must stay — virtual rewrite would change dispatch")
        assertEquals("pkg/Child", call.owner, "the owner itself is never rewritten either")
    }
}
