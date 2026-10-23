package xyz.wagyourtail.unimined.mapping.cli

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode

/** end-to-end smoke tests for the CLI — tiny v2 mappings + an ASM-built jar */
class CliTest {

    private fun classBytes(name: String, method: String): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        cw.visitSource("${name.substringAfterLast('/')}.java", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null)
        mv.visitCode()
        val l0 = Label(); mv.visitLabel(l0); mv.visitLineNumber(3, l0)
        val l1 = Label(); mv.visitLabel(l1); mv.visitLineNumber(4, l1)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun writeJar(path: Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
    }

    /** build a `0x4646` linemap extra field — `u16 tag, u16 len, u8 ver=1`,
     *  then LE `(u16 srcLine, u16 origLine)` pairs */
    private fun linemapExtra(vararg pairs: Pair<Int, Int>): ByteArray {
        val body = ByteBuffer.allocate(1 + pairs.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        body.put(1)
        for ((a, b) in pairs) { body.putShort(a.toShort()); body.putShort(b.toShort()) }
        val field = ByteBuffer.allocate(4 + body.position()).order(ByteOrder.LITTLE_ENDIAN)
        field.putShort(0x4646.toShort()); field.putShort(body.position().toShort())
        field.put(body.array(), 0, body.position())
        return field.array()
    }

    private fun baseArgs(tiny: Path) = arrayOf(
        "-i", tiny.toString(),
        "--unmapped-ns", "obf",
        "--requires", "obf",
        "--provides", "named",
    )

    private fun writeTiny(dir: Path): Path = dir.resolve("m.tiny").apply {
        writeText("tiny\t2\t0\tobf\tnamed\nc\ta/A\tx/Target\n\tm\t()V\tm_1\tdoIt\n")
    }

    @Test
    fun remapEndToEnd() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")
        writeJar(input, mapOf("a/A.class" to classBytes("a/A", "m_1")))

        main(baseArgs(writeTiny(dir)) + arrayOf(
            "remap", input.toString(), output.toString(), "--from", "obf", "--to", "named",
        ))

        val names = ZipFile(output.toFile()).use { z -> z.entries().asSequence().map { it.name }.toList() }
        assertTrue("x/Target.class" in names, "expected x/Target.class, got $names")
        val node = ClassNode().also {
            ZipFile(output.toFile()).use { z ->
                ClassReader(z.getInputStream(z.getEntry("x/Target.class"))).accept(it, 0)
            }
        }
        assertTrue(node.methods.any { it.name == "doIt" }, "method not remapped: ${node.methods.map { it.name }}")
    }

    @Test
    fun exportRoundTrip() {
        val dir = createTempDirectory()
        val out = dir.resolve("out.tiny")

        main(baseArgs(writeTiny(dir)) + arrayOf("export", "tinyv2", out.toString()))

        val text = Files.readString(out)
        assertTrue("a/A" in text && "x/Target" in text, "round-trip lost rows: $text")
    }

    @Test
    fun linemapApplies() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val srcJar = dir.resolve("src-lm.jar")
        val output = dir.resolve("lm-out.jar")
        writeJar(input, mapOf("a/A.class" to classBytes("a/A", "m_1")))
        ZipOutputStream(Files.newOutputStream(srcJar)).use { z ->
            z.putNextEntry(ZipEntry("a/A.java").apply { extra = linemapExtra(3 to 30, 4 to 40) })
            z.write("package a;\npublic class A {}\n".toByteArray())
            z.closeEntry()
        }

        main(arrayOf("linemap", input.toString(), srcJar.toString(), output.toString()))

        val node = ClassNode().also {
            ZipFile(output.toFile()).use { z ->
                ClassReader(z.getInputStream(z.getEntry("a/A.class"))).accept(it, 0)
            }
        }
        val lines = node.methods.flatMap { m ->
            m.instructions.toArray().filterIsInstance<LineNumberNode>().map { it.line }
        }
        assertEquals(listOf(30, 40), lines)
    }

}
