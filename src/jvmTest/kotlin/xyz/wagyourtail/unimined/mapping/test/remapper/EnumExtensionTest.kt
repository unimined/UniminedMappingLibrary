package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.getstatics
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.ints
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.putstatics
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.strings
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `EnumExtensionElement` — javac `<clinit>`/`$VALUES` surgery: constant ADD
 * clones the last init block, REMOVE splices it out and renumbers ordinals
 * and array indices.
 */
class EnumExtensionTest {

    private fun enumClinit(self: String, constants: List<String>): MethodNode =
        MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitCode()
            constants.forEachIndexed { i, c ->
                visitTypeInsn(Opcodes.NEW, self)
                visitInsn(Opcodes.DUP)
                visitLdcInsn(c)
                pushInt(i)
                visitMethodInsn(Opcodes.INVOKESPECIAL, self, "<init>", "(Ljava/lang/String;I)V", false)
                visitFieldInsn(Opcodes.PUTSTATIC, self, c, "L$self;")
            }
            pushInt(constants.size)
            visitTypeInsn(Opcodes.ANEWARRAY, self)
            constants.forEachIndexed { i, c ->
                visitInsn(Opcodes.DUP)
                pushInt(i)
                visitFieldInsn(Opcodes.GETSTATIC, self, c, "L$self;")
                visitInsn(Opcodes.AASTORE)
            }
            visitFieldInsn(Opcodes.PUTSTATIC, self, "\$VALUES", "[L$self;")
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }

    private fun MethodNode.pushInt(n: Int) = when (n) {
        in 0..5 -> visitInsn(Opcodes.ICONST_0 + n)
        else -> visitIntInsn(Opcodes.BIPUSH, n)
    }

    private fun enumBytes(constants: List<String>) = classBytes(
        "pkg/E", Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_ENUM or Opcodes.ACC_SUPER,
    ) {
        for (c in constants) {
            fields.add(FieldNode(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_ENUM,
                c, "Lpkg/E;", null, null,
            ))
        }
        fields.add(FieldNode(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
            "\$VALUES", "[Lpkg/E;", null, null,
        ))
        methods.add(enumClinit("pkg/E", constants))
    }

    @Test
    fun addAndRemoveEnumConstants() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("pkg/E", "pkg/E") // names unchanged — only metadata
        cls.addMetadata(EnumExtensionElement(AddRemove.REMOVE, UnqualifiedName.unchecked("B"), named))
        cls.addMetadata(EnumExtensionElement(AddRemove.ADD, UnqualifiedName.unchecked("D"), named))

        writeJar(input, mapOf("pkg/E.class" to enumBytes(listOf("A", "B", "C"))))
        AsmRemapper.remap(input, output, tree, obf, named)

        val e = readClasses(output).getValue("pkg/E")
        // constant fields: A, C, D (B gone, D appended)
        assertEquals(
            listOf("A", "C", "D"),
            e.fields.filter { it.access and Opcodes.ACC_ENUM != 0 }.map { it.name },
        )

        val clinit = e.methods.first { it.name == "<clinit>" }
        assertEquals(listOf("A", "C", "D"), clinit.putstatics().filter { it.name != "\$VALUES" }.map { it.name })
        assertEquals(listOf("A", "C", "D"), clinit.strings())
        assertEquals(listOf("A", "C", "D"), clinit.getstatics().map { it.name })
        // ordinals in ctor calls and array indices renumbered 0,1,2; size push = 3
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2), clinit.ints())
    }

    @Test
    fun skipsNonEnum() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("pkg/E", "pkg/E")
        cls.addMetadata(EnumExtensionElement(AddRemove.ADD, UnqualifiedName.unchecked("D"), named))

        writeJar(input, mapOf("pkg/E.class" to classBytes("pkg/E")))
        AsmRemapper.remap(input, output, tree, obf, named)

        // non-enum: warn+skip, class passes through untouched
        assertEquals(emptyList(), readClasses(output).getValue("pkg/E").fields)
    }

}
