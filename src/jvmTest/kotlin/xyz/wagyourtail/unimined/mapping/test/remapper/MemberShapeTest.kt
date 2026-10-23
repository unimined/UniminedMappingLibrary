package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.element.InnerType
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
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
 * Member-shape edge cases: generic signatures, annotation attribute names,
 * record components, inner-class attr rewrites, wildcard LV mappings.
 */
class MemberShapeTest {

    @Test
    fun remapsGenericSignatures() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")
        tree.cls("pkg/B", "net/mc/B")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "Ljava/util/List;", "Ljava/util/List<Lpkg/B;>;", null))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)
        val f = readClasses(output).getValue("net/minecraft/Example").fields.first()
        assertEquals("Ljava/util/List<Lnet/mc/B;>;", f.signature)
    }

    @Test
    fun remapsAnnotationAttributeNames() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")
        val ann = tree.cls("pkg/Ann", "net/mc/Ann")
        ann.method("field_1;()I", "mappedField;()I")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                visibleAnnotations = mutableListOf(
                    AnnotationNode("Lpkg/Ann;").apply {
                        visit("field_1", 42)
                        visitEnd()
                    }
                )
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)
        val a = readClasses(output).getValue("net/minecraft/Example").visibleAnnotations.first()
        assertEquals("Lnet/mc/Ann;", a.desc)
        assertEquals(listOf("mappedField", 42), a.values)
    }

    @Test
    fun remapsRecordComponents() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("pkg/R", "net/mc/R")
        cls.field("field_1;I", "mappedField;I")

        writeJar(input, mapOf(
            "pkg/R.class" to classBytes("pkg/R", Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER or Opcodes.ACC_RECORD) {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL, "field_1", "I", null, null))
                recordComponents = mutableListOf(RecordComponentNode("field_1", "I", null))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)
        val r = readClasses(output).getValue("net/mc/R")
        assertEquals("mappedField", r.recordComponents.first().name)
    }

    @Test
    fun rewritesInnerClassAttribute() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("pkg/Outer", "net/mc/Outer")
        val inner = tree.cls("pkg/Outer\$Inner", "net/mc/Outer\$Inner")
        inner.addMetadata(InnerClassElement(
            InnerType.INNER,
            mapOf(named to ("NewInner" to FullyQualifiedName.unchecked("Lnet/mc/Outer;"))),
        ))

        writeJar(input, mapOf(
            "pkg/Outer\$Inner.class" to classBytes("pkg/Outer\$Inner") {
                innerClasses.add(InnerClassNode("pkg/Outer\$Inner", "pkg/Outer", "Inner", Opcodes.ACC_STATIC))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)
        val icn = readClasses(output).getValue("net/mc/Outer\$Inner").innerClasses.first()
        assertEquals("net/mc/Outer\$Inner", icn.name)
        assertEquals("net/mc/Outer", icn.outerName)
        assertEquals("NewInner", icn.innerName)
    }

    @Test
    fun wildcardGroupsLocalNamesByDesc() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        cls.method("add;(II)I", "sum;(II)I")
        // wildcard covers every (II)I method's lvar at slot 3
        val w = cls.wildcard(WildcardType.METHOD, mapOf(named to FieldOrMethodDescriptor.unchecked("(II)I")))
        w.localVariable(3, 0, mapOf(named to UnqualifiedName.unchecked("total")))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null).apply {
                    visitCode()
                    val l0 = Label(); val l1 = Label()
                    visitLabel(l0)
                    visitVarInsn(Opcodes.ILOAD, 1)
                    visitVarInsn(Opcodes.ILOAD, 2)
                    visitInsn(Opcodes.IADD)
                    visitVarInsn(Opcodes.ISTORE, 3)
                    visitVarInsn(Opcodes.ILOAD, 3)
                    visitInsn(Opcodes.IRETURN)
                    visitLabel(l1)
                    visitLocalVariable("x", "I", null, l0, l1, 3)
                    visitMaxs(2, 4)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)
        val sum = readClasses(output).getValue("net/minecraft/Example").methods.first { it.name == "sum" }
        assertNotNull(sum.localVariables.firstOrNull { it.name == "total" && it.index == 3 },
            "wildcard LV mapping should rename slot-3 var: ${sum.localVariables.map { it.name }}")
    }

}
