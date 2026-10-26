package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.AddRemoveClear
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class AsmRemapperTest {

    private val obf = Namespace("intermediary")
    private val named = Namespace("named")

    // --- fixtures ---

    private fun classBytes(name: String, access: Int = Opcodes.ACC_PUBLIC, block: ClassNode.() -> Unit = {}): ByteArray {
        val node = ClassNode().apply {
            this.access = access
            this.name = name
            this.superName = "java/lang/Object"
            this.version = Opcodes.V17
            block()
        }
        val writer = org.objectweb.asm.ClassWriter(0)
        node.accept(writer)
        return writer.toByteArray()
    }

    private fun voidMethod(name: String, access: Int = Opcodes.ACC_PUBLIC): MethodNode =
        MethodNode(access, name, "()V", null, null).apply {
            visitCode()
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 1)
            visitEnd()
        }

    private fun writeJar(path: Path, entries: Map<String, ByteArray>, manifest: Manifest? = null) {
        val stream = if (manifest != null) JarOutputStream(Files.newOutputStream(path), manifest)
            else ZipOutputStream(Files.newOutputStream(path))
        stream.use { jos ->
            for ((name, bytes) in entries) {
                jos.putNextEntry(ZipEntry(name))
                jos.write(bytes)
                jos.closeEntry()
            }
        }
    }

    private fun readClasses(path: Path): Map<String, ClassNode> {
        val out = mutableMapOf<String, ClassNode>()
        ZipFile(path.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().filter { it.name.endsWith(".class") }) {
                val node = ClassNode()
                org.objectweb.asm.ClassReader(zin.getInputStream(entry)).accept(node, 0)
                out[node.name] = node
            }
        }
        return out
    }

    private fun tree(): MutableMappingTree {
        val tree = MutableMappingTree(setOf(obf, named))
        val cls = tree.clazz(
            mapOf(
                obf to InternalName.unchecked("eqm"),
                named to InternalName.unchecked("net/minecraft/Example"),
            )
        )
        cls.field(
            mapOf(
                obf to FieldNameAndDescriptor.unchecked("field_1;I"),
                named to FieldNameAndDescriptor.unchecked("mappedField;I"),
            )
        )
        cls.method(
            mapOf(
                obf to MethodNameAndDescriptor.unchecked("method_1;()V"),
                named to MethodNameAndDescriptor.unchecked("mappedMethod;()V"),
            )
        )
        return tree
    }

    // --- tests ---

    @Test
    fun remapsClassAndMembers() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
                methods.add(voidMethod("method_1"))
            },
        ))

        AsmRemapper.remap(input, output, tree(), obf, named)

        val classes = readClasses(output)
        val cls = classes["net/minecraft/Example"]
        assertNotNull(cls, "expected net/minecraft/Example.class in output")
        assertTrue(cls.fields.any { it.name == "mappedField" && it.desc == "I" })
        assertTrue(cls.methods.any { it.name == "mappedMethod" && it.desc == "()V" })
    }

    @Test
    fun remapsReferencesInCode() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
                methods.add(voidMethod("method_1"))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "caller", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKEVIRTUAL, "eqm", "method_1", "()V", false)
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitFieldInsn(Opcodes.GETFIELD, "eqm", "field_1", "I")
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree(), obf, named)

        val cls = readClasses(output).getValue("net/minecraft/Example")
        val caller = cls.methods.first { it.name == "caller" }
        val insns = caller.instructions.toArray().filterIsInstance<org.objectweb.asm.tree.MethodInsnNode>()
        assertEquals("mappedMethod", insns.first().name)
        val fieldInsn = caller.instructions.toArray().filterIsInstance<org.objectweb.asm.tree.FieldInsnNode>().first()
        assertEquals("mappedField", fieldInsn.name)
    }

    @Test
    fun appliesAccessExceptionInterfaceSealAnnotationMetadata() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = tree()
        val cls = tree.classes.single() as xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
        val method = cls.methods.single() as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping

        cls.addMetadata(InterfaceElement(AddRemove.ADD, ClassTypeSignature.unchecked("Ltest/Iface;"), named))
        cls.addMetadata(SealElement(AddRemoveClear.ADD, InternalName.unchecked("test/Sub"), named))
        cls.addMetadata(AnnotationElement(AddRemoveModify.ADD, named, Annotation.unchecked("@Ltest/Marker;(value=42)")))
        method.addMetadata(AccessElement(AddRemove.ADD, AccessFlag.PUBLIC, AccessConditions.ALL))
        method.addMetadata(ExceptionElement(AddRemove.ADD, InternalName.unchecked("test/MyException"), named))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(voidMethod("method_1", Opcodes.ACC_PRIVATE))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        assertTrue("test/Iface" in out.interfaces)
        assertEquals(listOf("test/Sub"), out.permittedSubclasses)
        assertTrue(out.visibleAnnotations.any { it.desc == "Ltest/Marker;" })
        val m = out.methods.first { it.name == "mappedMethod" }
        assertTrue(m.access and Opcodes.ACC_PUBLIC != 0)
        assertTrue(m.access and Opcodes.ACC_PRIVATE == 0)
        assertTrue("test/MyException" in m.exceptions)
    }

    @Test
    fun emitsSyntheticBridge() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = tree()
        val cls = tree.classes.single() as xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
        val method = cls.methods.single() as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
        method.addMetadata(SyntheticBridgeElement(mapOf(named to UnqualifiedName.unchecked("alsoImpl"))))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(voidMethod("method_1"))
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        val bridge = out.methods.firstOrNull { it.name == "alsoImpl" }
        assertNotNull(bridge, "expected bridge method 'alsoImpl'")
        assertTrue(bridge.access and Opcodes.ACC_BRIDGE != 0)
        assertTrue(bridge.access and Opcodes.ACC_SYNTHETIC != 0)
        assertEquals("()V", bridge.desc)
    }

    @Test
    fun remapsParamsAndLocals() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = MutableMappingTree(setOf(obf, named))
        val cls = tree.clazz(
            mapOf(
                obf to InternalName.unchecked("eqm"),
                named to InternalName.unchecked("net/minecraft/Example"),
            )
        )
        val m = cls.method(
            mapOf(
                obf to MethodNameAndDescriptor.unchecked("add;(II)I"),
                named to MethodNameAndDescriptor.unchecked("sum;(II)I"),
            )
        )
        m.parameter(0, 1, mapOf(named to UnqualifiedName.unchecked("first")))
        m.parameter(1, 2, mapOf(named to UnqualifiedName.unchecked("second")))
        m.localVariable(3, 0, mapOf(named to UnqualifiedName.unchecked("total")))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null).apply {
                    visitParameter("a", 0)
                    visitParameter("b", 0)
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

        val out = readClasses(output).getValue("net/minecraft/Example")
        val sum = out.methods.first { it.name == "sum" }
        assertEquals(listOf("first", "second"), sum.parameters.map { it.name })
        // the LVT arg entries should agree with MethodParameters
        assertTrue(sum.localVariables.any { it.name == "total" && it.index == 3 })
    }

    @Test
    fun stripsSignaturesAndRemapsManifestAndServices() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = "eqm"

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") { methods.add(voidMethod("method_1")) },
            "META-INF/TEST.SF" to "Signature-Version: 1.0".toByteArray(),
            "META-INF/services/eqm" to "eqm".toByteArray(),
        ), manifest)

        AsmRemapper.remap(input, output, tree(), obf, named)

        ZipFile(output.toFile()).use { zin ->
            assertNull(zin.getEntry("META-INF/TEST.SF"), "signature file should be stripped")
            val svc = zin.getEntry("META-INF/services/net.minecraft.Example")
            assertNotNull(svc, "service file should be renamed")
            assertEquals("net.minecraft.Example", zin.getInputStream(svc).readBytes().toString(Charsets.UTF_8))
            val mf = Manifest(zin.getInputStream(zin.getEntry("META-INF/MANIFEST.MF")))
            assertEquals("net.minecraft.Example", mf.mainAttributes.getValue("Main-Class"))
        }
    }

}
