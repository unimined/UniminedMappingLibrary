package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.MethodNode
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
import kotlin.test.assertTrue

/** Option flags: frames, source filenames, field-desc matching, LV handling. */
class OptionsTest {

    private fun framedMethod(): MethodNode =
        MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null).apply {
            visitCode()
            val l0 = Label(); val l1 = Label()
            visitFrame(org.objectweb.asm.Opcodes.F_SAME, 0, null, 0, null)
            visitLabel(l0)
            visitInsn(Opcodes.RETURN)
            visitLabel(l1)
            visitMaxs(0, 1)
            visitEnd()
        }

    @Test
    fun removeFramesStripsStackMapTable() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") { methods.add(framedMethod()) }))

        // default: frames pass through
        val kept = dir.resolve("kept.jar")
        AsmRemapper.remap(input, kept, tree, obf, named)
        assertTrue(
            readClasses(kept).getValue("net/minecraft/Example")
                .methods.first { it.name == "m" }.instructions.toArray().any { it is FrameNode },
            "frames should survive default remap",
        )

        // removeFrames: stripped
        AsmRemapper.remap(input, output, tree, obf, named, Options(removeFrames = true))
        assertTrue(
            readClasses(output).getValue("net/minecraft/Example")
                .methods.first { it.name == "m" }.instructions.toArray().none { it is FrameNode },
            "frames should be stripped with removeFrames",
        )
    }

    @Test
    fun rebuildSourceFilenamesUpdatesSourceFile() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") { sourceFile = "eqm.java" },
        ))

        AsmRemapper.remap(input, output, tree, obf, named, Options(rebuildSourceFilenames = true))
        assertEquals("Example.java", readClasses(output).getValue("net/minecraft/Example").sourceFile)
    }

    @Test
    fun renameInvalidLocalsGeneratesNames() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null).apply {
                    visitCode()
                    val l0 = Label(); val l1 = Label()
                    visitLabel(l0)
                    visitInsn(Opcodes.ICONST_1)
                    visitVarInsn(Opcodes.ISTORE, 1)
                    visitInsn(Opcodes.RETURN)
                    visitLabel(l1)
                    // obfuscated-jar style: non-identifier name
                    visitLocalVariable("0x", "I", null, l0, l1, 1)
                    visitMaxs(1, 2)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named, Options(renameInvalidLocals = true))
        val lv = readClasses(output).getValue("net/minecraft/Example")
            .methods.first { it.name == "m" }.localVariables.first { it.index == 1 }
        assertTrue(lv.name.first().isJavaIdentifierStart() && lv.name.all { it.isJavaIdentifierPart() },
            "expected generated valid name, got '${lv.name}'")
    }

    @Test
    fun skipLocalVariableMappingLeavesNames() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        val m = cls.method("add;(II)I", "sum;(II)I")
        m.parameter(0, 1, mapOf(named to xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("first")))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null).apply {
                    visitParameter("a", 0)
                    visitParameter("b", 0)
                    visitCode()
                    visitVarInsn(Opcodes.ILOAD, 1)
                    visitVarInsn(Opcodes.ILOAD, 2)
                    visitInsn(Opcodes.IADD)
                    visitInsn(Opcodes.IRETURN)
                    visitMaxs(2, 3)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named, Options(skipLocalVariableMapping = true))
        val sum = readClasses(output).getValue("net/minecraft/Example").methods.first { it.name == "sum" }
        assertEquals(listOf("a", "b"), sum.parameters.map { it.name })
    }

}
