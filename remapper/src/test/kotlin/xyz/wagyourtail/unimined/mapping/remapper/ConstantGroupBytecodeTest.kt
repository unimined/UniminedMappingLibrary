package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** bytecode half of constant uninlining — [xyz.wagyourtail.unimined.mapping.remapper.asm.ConstantGroupEmitter] */
class ConstantGroupBytecodeTest {

    private val obf = Namespace("intermediary")
    private val named = Namespace("named")

    // --- fixtures ---

    private fun classBytes(name: String, block: ClassNode.() -> Unit = {}): ByteArray {
        val node = ClassNode().apply {
            access = Opcodes.ACC_PUBLIC
            this.name = name
            superName = "java/lang/Object"
            version = Opcodes.V17
            block()
        }
        val writer = org.objectweb.asm.ClassWriter(0)
        node.accept(writer)
        return writer.toByteArray()
    }

    private fun method(
        name: String,
        desc: String,
        vararg insns: AbstractInsnNode,
        access: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
    ): MethodNode = MethodNode(access, name, desc, null, null).apply {
        visitCode()
        insns.forEach { instructions.add(it) }
        visitMaxs(8, 4)
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

    private fun readClass(path: Path, name: String): ClassNode {
        ZipFile(path.toFile()).use { zin ->
            val node = ClassNode()
            org.objectweb.asm.ClassReader(zin.getInputStream(zin.getEntry("$name.class"))).accept(node, 0)
            return node
        }
    }

    private fun tree(block: MutableMappingTree.() -> Unit = {}): MutableMappingTree =
        MutableMappingTree(setOf(obf, named)).apply(block)

    /**
     * flags group: class `a`→`net/mc/Flags` with fields `f_1`→`FLAG_A`,
     * `f_2`→`FLAG_B`; constant `3` → `f_1 | f_2`. [targetPairs] are
     * (fqn, paramIdx) — none defaults to a single global (null) target.
     */
    private fun flagsGroup(
        tree: MutableMappingTree,
        baseNs: Namespace = obf,
        vararg targetPairs: Pair<FullyQualifiedName?, Int?>,
    ) {
        val cls = tree.clazz(
            mapOf(
                obf to InternalName.unchecked("a"),
                named to InternalName.unchecked("net/mc/Flags"),
            )
        )
        cls.field(
            mapOf(
                obf to FieldNameAndDescriptor.unchecked("f_1;I"),
                named to FieldNameAndDescriptor.unchecked("FLAG_A;I"),
            )
        )
        cls.field(
            mapOf(
                obf to FieldNameAndDescriptor.unchecked("f_2;I"),
                named to FieldNameAndDescriptor.unchecked("FLAG_B;I"),
            )
        )
        val group = MutableConstantGroupMapping(InlineType.PLAIN, name = "flags", baseNs = baseNs)
        val owner = InternalName.unchecked("a")
        group.constant(owner, UnqualifiedName.unchecked("f_1"), FieldDescriptor.unchecked("I"))
        group.constant(owner, UnqualifiedName.unchecked("f_2"), FieldDescriptor.unchecked("I"))
        group.expression(Constant.unchecked("3"), Expression.unchecked("La;f_1;I | La;f_2;I"))
        for ((t, idx) in targetPairs) group.target(t, idx)
        (tree.constantGroups as MutableList).add(group)
    }

    private fun insnText(m: MethodNode): List<String> = m.instructions.toArray().map {
        when (it) {
            is FieldInsnNode -> "GETSTATIC ${it.owner}.${it.name}"
            is MethodInsnNode -> "INVOKE ${it.owner}.${it.name}"
            is InsnNode -> "OP_${it.opcode}"
            is IntInsnNode -> "OP_${it.opcode}:${it.operand}"
            else -> "insn_${it.opcode}"
        }
    }

    private fun flagsClass(): Pair<String, ByteArray> = "a.class" to classBytes("a") {
        fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "f_1", "I", null, null))
        fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "f_2", "I", null, null))
    }

    private fun bClassMapping(tree: MutableMappingTree) {
        tree.clazz(
            mapOf(
                obf to InternalName.unchecked("b"),
                named to InternalName.unchecked("net/mc/B"),
            )
        )
    }

    // --- tests ---

    @Test
    fun unpicksGlobalLiteral() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        flagsGroup(tree) // single null target → global

        writeJar(input, mapOf(
            flagsClass(),
            "b.class" to classBytes("b") {
                methods.add(method("go", "()V",
                    InsnNode(Opcodes.ICONST_3), InsnNode(Opcodes.POP), InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("GETSTATIC net/mc/Flags.FLAG_A"), "expected FLAG_A getstatic: $text")
        assertTrue(text.contains("GETSTATIC net/mc/Flags.FLAG_B"), "expected FLAG_B getstatic: $text")
        assertTrue(text.contains("OP_${Opcodes.IOR}"), "expected IOR: $text")
        assertFalse(text.contains("OP_${Opcodes.ICONST_3}"), "literal 3 should be replaced: $text")
    }

    @Test
    fun unpicksMethodArgLiteral() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        flagsGroup(tree, targetPairs = arrayOf(FullyQualifiedName.unchecked("Lb;use(I)V") to null))

        writeJar(input, mapOf(
            "b.class" to classBytes("b") {
                methods.add(method("use", "(I)V", InsnNode(Opcodes.RETURN)))
                methods.add(method("go", "()V",
                    InsnNode(Opcodes.ICONST_3),
                    MethodInsnNode(Opcodes.INVOKESTATIC, "b", "use", "(I)V", false),
                    InsnNode(Opcodes.ICONST_3), InsnNode(Opcodes.POP),
                    InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("GETSTATIC net/mc/Flags.FLAG_A"), "arg literal unpicked: $text")
        assertTrue(text.contains("OP_${Opcodes.ICONST_3}"), "non-arg literal 3 should survive: $text")
    }

    /** unpick-format group: const expressions carry no literal value — the
     *  emitter evaluates them via the input jar's field ConstantValues */
    @Test
    fun unpicksEvaluatedFieldRef() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        val cls = tree.clazz(
            mapOf(
                obf to InternalName.unchecked("a"),
                named to InternalName.unchecked("net/mc/Flags"),
            )
        )
        cls.field(
            mapOf(
                obf to FieldNameAndDescriptor.unchecked("f_1;I"),
                named to FieldNameAndDescriptor.unchecked("FLAG_A;I"),
            )
        )
        val group = MutableConstantGroupMapping(InlineType.PLAIN, name = "flags", baseNs = obf)
        group.constant(InternalName.unchecked("a"), UnqualifiedName.unchecked("f_1"), FieldDescriptor.unchecked("I"))
        // marks the group as unpick-origin; the `value` is not a literal
        (group.metadata as MutableList).add(
            xyz.wagyourtail.unimined.mapping.element.metadata.impl.AnnotationElementData(
                xyz.wagyourtail.unimined.mapping.element.AddRemoveModify.ADD, obf,
                xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation.unchecked(
                    "@Lunimined/unpick/DataType;(value=\"int\")")))
        group.expression(Constant.unchecked("La;f_1;I"), Expression.unchecked("La;f_1;I"))
        (tree.constantGroups as MutableList).add(group)

        writeJar(input, mapOf(
            "a.class" to classBytes("a") {
                fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "f_1", "I", null, 3))
            },
            "b.class" to classBytes("b") {
                methods.add(method("go", "()V",
                    InsnNode(Opcodes.ICONST_3), InsnNode(Opcodes.POP), InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("GETSTATIC net/mc/Flags.FLAG_A"),
            "literal 3 resolved to f_1's value → FLAG_A: $text")
        assertFalse(text.contains("OP_${Opcodes.ICONST_3}"), "literal 3 should be replaced: $text")
    }

    /** `e 42 "20+22"` — the expression folds to the declared value, so the
     *  literal is unpicked into the expression's emitted instructions */
    @Test
    fun replacesMatchingDeclaredValue() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        val group = MutableConstantGroupMapping(InlineType.PLAIN, name = "g", baseNs = obf)
        group.expression(Constant.unchecked("42"), Expression.unchecked("20 + 22"))
        (tree.constantGroups as MutableList).add(group)

        writeJar(input, mapOf(
            "b.class" to classBytes("b") {
                methods.add(method("go", "()V",
                    IntInsnNode(Opcodes.BIPUSH, 42), InsnNode(Opcodes.POP), InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("OP_${Opcodes.IADD}"), "literal 42 should become 20+22: $text")
        assertFalse(text.contains("OP_${Opcodes.BIPUSH}:42"), "literal 42 should be replaced: $text")
    }

    /** `e 42 "20+23"` — the expression folds to a *different* value than
     *  declared: the entry is rejected, the literal survives untouched */
    @Test
    fun rejectsMismatchingDeclaredValue() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        val group = MutableConstantGroupMapping(InlineType.PLAIN, name = "g", baseNs = obf)
        group.expression(Constant.unchecked("42"), Expression.unchecked("20 + 23"))
        (tree.constantGroups as MutableList).add(group)

        writeJar(input, mapOf(
            "b.class" to classBytes("b") {
                methods.add(method("go", "()V",
                    IntInsnNode(Opcodes.BIPUSH, 42), InsnNode(Opcodes.POP), InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("OP_${Opcodes.BIPUSH}:42"), "mismatched entry must leave literal 42: $text")
        assertFalse(text.contains("OP_${Opcodes.IADD}"), "no replacement expected: $text")
    }

    /** `e 3 "La;f_1;I"` with the jar's f_1 ConstantValue = 4 — field refs are
     *  resolved from the input jar, so the mismatch is still caught */
    @Test
    fun rejectsMismatchingFieldRefValue() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        tree.clazz(mapOf(
            obf to InternalName.unchecked("a"),
            named to InternalName.unchecked("net/mc/Flags"),
        ))
        val group = MutableConstantGroupMapping(InlineType.PLAIN, name = "g", baseNs = obf)
        group.expression(Constant.unchecked("3"), Expression.unchecked("La;f_1;I"))
        (tree.constantGroups as MutableList).add(group)

        writeJar(input, mapOf(
            "a.class" to classBytes("a") {
                fields.add(FieldNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "f_1", "I", null, 4))
            },
            "b.class" to classBytes("b") {
                methods.add(method("go", "()V",
                    InsnNode(Opcodes.ICONST_3), InsnNode(Opcodes.POP), InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertTrue(text.contains("OP_${Opcodes.ICONST_3}"), "f_1 evaluates to 4 ≠ declared 3 — literal survives: $text")
        assertFalse(text.any { it.startsWith("GETSTATIC") }, "no replacement expected: $text")
    }

    @Test
    fun unpicksOnlyTargetedParam() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")
        val tree = tree { bClassMapping(this) }
        flagsGroup(tree, targetPairs = arrayOf(FullyQualifiedName.unchecked("Lb;use(II)V") to 1))

        writeJar(input, mapOf(
            "b.class" to classBytes("b") {
                methods.add(method("use", "(II)V", InsnNode(Opcodes.RETURN)))
                methods.add(method("go", "()V",
                    InsnNode(Opcodes.ICONST_3), // arg0 — should survive
                    InsnNode(Opcodes.ICONST_3), // arg1 — unpicked
                    MethodInsnNode(Opcodes.INVOKESTATIC, "b", "use", "(II)V", false),
                    InsnNode(Opcodes.RETURN)))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val go = readClass(output, "net/mc/B").methods.first { it.name == "go" }
        val text = insnText(go)
        assertEquals(1, text.count { it == "OP_${Opcodes.ICONST_3}" }, "exactly one literal 3 survives: $text")
        assertTrue(text.contains("GETSTATIC net/mc/Flags.FLAG_A"), "arg1 literal unpicked: $text")
        assertTrue(text.contains("OP_${Opcodes.IOR}"), "expected IOR: $text")
    }
}
