package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
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
 * Same-source-name overloads mapping to the same target name are not one
 * member: `a(FF)V` on the parent and `a(IZ)V` on the child are distinct
 * rows even when both are named `x` in the target namespace. A virtual ref
 * through the child must keep the descriptor it was written with — primitive
 * descriptor parts can never be rewritten by a class remap.
 */
class OverloadMemberRefTest {

    private fun emptyMethod(name: String, desc: String) =
        MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null).apply {
            visitCode()
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 1)
            visitEnd()
        }

    /** pa declares `a(FF)V`; cb extends pa, declares only `a(IZ)V`, and
     *  invokes the inherited `a(FF)V` through its own owner. */
    private fun overloadRefJar(includeOverride: Boolean = false) = mapOf(
        "pa.class" to classBytes("pa") {
            methods.add(emptyMethod("a", "(FF)V"))
        },
        "cb.class" to classBytes("cb") {
            superName = "pa"
            methods.add(emptyMethod("a", "(IZ)V"))
            if (includeOverride) methods.add(emptyMethod("a", "(FF)V"))
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitInsn(Opcodes.FCONST_0)
                visitInsn(Opcodes.FCONST_1)
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cb", "a", "(FF)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(3, 1)
                visitEnd()
            })
        },
    )

    private fun overloadTree() = treeOf().apply {
        cls("pa", "pkg/Parent").method("a;(FF)V", "x;(FF)V")
        cls("cb", "pkg/Child").method("a;(IZ)V", "x;(IZ)V")
    }

    @Test
    fun inheritedOverloadRefKeepsItsPrimitiveDescriptor() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        writeJar(mc, overloadRefJar())
        AsmRemapper.remap(mc, out, listOf(overloadTree()), obf, named)

        val child = readClasses(out).getValue("pkg/Child")
        val call = child.methods.single { it.name == "call" }.instructions.toArray()
            .filterIsInstance<MethodInsnNode>().single { it.name == "x" }
        assertEquals("(FF)V", call.desc,
            "the inherited parent overload's ref must remain (FF)V — never collapse to the child's (IZ)V overload")
        assertEquals("(IZ)V", assertNotNull(child.methods.singleOrNull { it.name == "x" })?.desc,
            "the child's own overload still remaps independently")
        assertEquals(0, child.methods.count { it.name == "x" && it.desc == "(FF)V" },
            "no spurious child override/bridge may be materialized for the inherited overload")
    }

    @Test
    fun sameDescriptorOverrideStillJoinsTheRenameChain() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        writeJar(mc, overloadRefJar(includeOverride = true))
        AsmRemapper.remap(mc, out, listOf(overloadTree()), obf, named)

        val child = readClasses(out).getValue("pkg/Child")
        assertNotNull(child.methods.singleOrNull { it.name == "x" && it.desc == "(FF)V" },
            "same-name+same-desc overrides must still propagate the parent's target name")
        assertNotNull(child.methods.singleOrNull { it.name == "x" && it.desc == "(IZ)V" },
            "the different-desc overload keeps its own row in the same target namespace")
    }
}
