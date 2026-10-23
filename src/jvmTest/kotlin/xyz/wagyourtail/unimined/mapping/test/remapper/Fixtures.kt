package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object Fixtures {

    val obf = Namespace("intermediary")
    val named = Namespace("named")

    fun classBytes(name: String, access: Int = Opcodes.ACC_PUBLIC, block: ClassNode.() -> Unit = {}): ByteArray {
        val node = ClassNode().apply {
            this.access = access
            this.name = name
            this.superName = "java/lang/Object"
            this.version = Opcodes.V17
            block()
        }
        val writer = ClassWriter(0)
        node.accept(writer)
        return writer.toByteArray()
    }

    fun voidMethod(name: String, access: Int = Opcodes.ACC_PUBLIC): MethodNode =
        MethodNode(access, name, "()V", null, null).apply {
            visitCode()
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 1)
            visitEnd()
        }

    fun writeJar(path: Path, entries: Map<String, ByteArray>, manifest: Manifest? = null) {
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

    fun readClasses(path: Path): Map<String, ClassNode> {
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

    fun readEntries(path: Path): Set<String> =
        ZipFile(path.toFile()).use { zin -> zin.entries().asSequence().map { it.name }.toSet() }

    fun treeOf(vararg namespaces: Namespace = arrayOf(obf, named)): MutableMappingTree =
        MutableMappingTree(namespaces.toSet())

    fun MutableMappingTree.cls(obfName: String, namedName: String): MutableClassMapping =
        clazz(mapOf(obf to InternalName.unchecked(obfName), named to InternalName.unchecked(namedName)))

    fun MutableClassMapping.method(obfEntry: String, namedEntry: String) =
        method(mapOf(
            obf to MethodNameAndDescriptor.unchecked(obfEntry),
            named to MethodNameAndDescriptor.unchecked(namedEntry),
        ))

    fun MutableClassMapping.field(obfEntry: String, namedEntry: String) =
        field(mapOf(
            obf to FieldNameAndDescriptor.unchecked(obfEntry),
            named to FieldNameAndDescriptor.unchecked(namedEntry),
        ))

    // --- instruction inspection helpers ---

    fun intValue(insn: AbstractInsnNode): Int? = when {
        insn is InsnNode && insn.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> insn.opcode - Opcodes.ICONST_0
        insn is IntInsnNode && (insn.opcode == Opcodes.BIPUSH || insn.opcode == Opcodes.SIPUSH) -> insn.operand
        insn is LdcInsnNode && insn.cst is Int -> insn.cst as Int
        else -> null
    }

    fun MethodNode.ints() = instructions.toArray().mapNotNull { intValue(it) }
    fun MethodNode.strings() = instructions.toArray().filterIsInstance<LdcInsnNode>().map { it.cst }
    fun MethodNode.putstatics() = instructions.toArray().filterIsInstance<FieldInsnNode>().filter { it.opcode == Opcodes.PUTSTATIC }
    fun MethodNode.getstatics() = instructions.toArray().filterIsInstance<FieldInsnNode>().filter { it.opcode == Opcodes.GETSTATIC }
    fun MethodNode.anewarray(): TypeInsnNode? =
        instructions.toArray().filterIsInstance<TypeInsnNode>().firstOrNull { it.opcode == Opcodes.ANEWARRAY }
    fun MethodNode.ctorCalls(): List<MethodInsnNode> =
        instructions.toArray().filterIsInstance<MethodInsnNode>().filter { it.name == "<init>" }

}
