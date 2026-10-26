package xyz.wagyourtail.unimined.mapping.remapper.source

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Vineflower `linemap` zip extra field (`-bsm=1`) — decompiled source line →
 * original bytecode line, stored per jar entry under extra-field id `0x4646`.
 *
 * Layout (little-endian): `u16 0x4646`, `u16 length`, `u8 version = 1`,
 * then `length-1` bytes of `u16 srcLine, u16 origLine` pairs.
 */
object Linemap {

    const val TAG: Short = 0x4646

    /** parse the `0x4646` field out of a raw extra-field byte array —
     *  `null` when absent/short/unknown version */
    fun parse(extra: ByteArray?): Map<Int, Int>? {
        if (extra == null || extra.size < 4) return null
        val buf = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 4) {
            val header = buf.short
            val length = buf.short.toInt() and 0xFFFF
            if (header == TAG) {
                if (length < 1 || buf.remaining() < length) return null
                if (buf.get() != 1.toByte()) return null
                val map = LinkedHashMap<Int, Int>()
                repeat((length - 1) / 4) {
                    if (buf.remaining() < 4) return null
                    map[buf.short.toInt() and 0xFFFF] = buf.short.toInt() and 0xFFFF
                }
                return map.takeIf { it.isNotEmpty() }
            }
            if (buf.remaining() < length) return null
            buf.position(buf.position() + length)
        }
        return null
    }

    /** rebuild a whole extra-field array containing a `0x4646` linemap,
     *  preserving any other extra fields already present */
    fun replace(extra: ByteArray?, lines: Map<Int, Int>): ByteArray {
        val body = ByteBuffer.allocate(1 + lines.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        body.put(1)
        lines.forEach { (k, v) -> body.putShort(k.toShort()); body.putShort(v.toShort()) }
        val field = ByteBuffer.allocate(4 + body.position()).order(ByteOrder.LITTLE_ENDIAN)
        field.putShort(TAG); field.putShort(body.position().toShort()); field.put(body.array(), 0, body.position())

        val out = ByteBuffer.allocate((extra?.size ?: 0) + field.position() + 4).order(ByteOrder.LITTLE_ENDIAN)
        if (extra != null) {
            val buf = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
            // copy non-linemap fields through
            while (buf.remaining() >= 4) {
                val header = buf.short; val length = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < length) break
                if (header != TAG) {
                    out.putShort(header); out.putShort(length.toShort())
                    val data = ByteArray(length); buf.get(data); out.put(data)
                } else buf.position(buf.position() + length)
            }
        }
        out.put(field.array(), 0, field.position())
        return out.array().copyOf(out.position())
    }

    /** shift a linemap by per-line deltas — [deltas] is `(sourceLine, delta)`
     *  sorted; a srcLine moves by the sum of deltas on earlier lines */
    fun shift(lines: Map<Int, Int>, deltas: List<Pair<Int, Int>>): Map<Int, Int> =
        if (deltas.isEmpty()) lines else lines.mapKeys { (srcLine, _) ->
            srcLine + deltas.filter { it.first < srcLine }.sumOf { it.second }
        }

    /**
     * Apply a linemapped sources jar to a binary jar — pipeline 2's last
     * step. For each `.class`, finds the source entry matching its
     * `SourceFile` (or `<name>.java` fallback), reads its `0x4646` linemap,
     * and rewrites `LineNumberNode`s so bytecode lines point at the
     * decompiled source. Non-class entries and entries without a linemap
     * pass through unchanged (extras preserved).
     */
    fun applyJar(binaryJar: Path, sourcesJar: Path, output: Path) {
        val extras = mutableMapOf<String, ByteArray>()
        ZipFile(sourcesJar.toFile()).use { z ->
            for (e in z.entries().asSequence()) if (e.extra != null) extras[e.name] = e.extra
        }
        ZipOutputStream(Files.newOutputStream(output).buffered()).use { zout ->
            ZipFile(binaryJar.toFile()).use { zin ->
                for (entry in zin.entries().asSequence().sortedBy { it.name }) {
                    if (entry.isDirectory) continue
                    val bytes = zin.getInputStream(entry).readBytes()
                    val out = ZipEntry(entry.name).apply {
                        time = 0L
                        entry.extra?.let { extra = it }
                    }
                    zout.putNextEntry(out)
                    if (entry.name.endsWith(".class")) {
                        val node = ClassNode()
                        ClassReader(bytes).accept(node, 0)
                        val sourceFile = node.sourceFile
                            ?: (node.name.substringAfterLast('/').substringBefore('$') + ".java")
                        val pkg = node.name.substringBeforeLast('/', "")
                        val sourceEntry = if (pkg.isEmpty()) sourceFile else "$pkg/$sourceFile"
                        val lines = parse(extras[sourceEntry])
                        if (lines != null) {
                            for (m in node.methods) for (insn in m.instructions) {
                                if (insn is LineNumberNode && insn.line in lines) insn.line = lines.getValue(insn.line)
                            }
                        }
                        val cw = ClassWriter(0)
                        node.accept(cw)
                        zout.write(cw.toByteArray())
                    } else {
                        zout.write(bytes)
                    }
                    zout.closeEntry()
                }
            }
        }
    }
}
