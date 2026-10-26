package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The extension pipeline: [BuiltinAsmExtensions] are the default
 * [Options.extensions], removable by list manipulation; user factories
 * append in pipeline order; `perClass` factories run inside the streaming
 * workers while non-perClass ones get the full analyze→postApply lifecycle
 * on the node path.
 */
class ExtensionPipelineTest {

    /** `pkg/Outer` → `a/b`, `pkg/Outer$Inner` → `a/b$C` */
    private fun tree() = treeOf().apply {
        cls("a/b", "pkg/Outer")
        cls("a/b\$C", "pkg/Outer\$Inner")
    }

    private fun innerClassBytes() = classBytes("pkg/Outer\$Inner") {
        sourceFile = "Outer.java"
    }

    private fun remapJar(options: Options, extraEntries: Map<String, ByteArray> = emptyMap()): java.nio.file.Path {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")
        writeJar(input, mapOf("pkg/Outer\$Inner.class" to innerClassBytes()) + extraEntries)
        AsmRemapper.remap(input, out, tree(), named, obf, options)
        return out
    }

    @Test
    fun sourceFileRebuiltByDefault() {
        val out = remapJar(Options())
        assertEquals("b.java", readClasses(out).getValue("a/b\$C").sourceFile)
    }

    @Test
    fun sourceFileStageDisableable() {
        val out = remapJar(Options(
            extensions = BuiltinAsmExtensions.defaults() - BuiltinAsmExtensions.SOURCE_FILES,
        ))
        assertEquals("Outer.java", readClasses(out).getValue("a/b\$C").sourceFile)
    }

    /** perClass factory wrapping a simple postApply extension */
    private fun perClassExt(postApply: (input: ClassNode, output: ClassNode, cls: ClassMapping?) -> Unit) =
        object : AsmExtensionFactory {
            override val perClass get() = true
            override fun create(ctx: xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext) =
                object : AsmExtension {
                    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) =
                        postApply(input, output, cls)
                }
        }

    @Test
    fun extensionsRunInListOrder() {
        // appended after the builtins → sees the rebuilt SourceFile
        var seenAfter = ""
        remapJar(Options(extensions = BuiltinAsmExtensions.defaults() +
            perClassExt { _, output, _ -> seenAfter = output.sourceFile }))
        assertEquals("b.java", seenAfter)

        // prepended before them → sees the raw SourceFile
        var seenBefore = ""
        remapJar(Options(extensions = listOf(
            perClassExt { _, output, _ -> seenBefore = output.sourceFile }
        ) + BuiltinAsmExtensions.defaults()))
        assertEquals("Outer.java", seenBefore)
    }

    @Test
    fun perClassExtensionRunsOnStreamingPath() {
        // on the streaming path only postApply/transformResource/resources run —
        // analyze is a node-path hook and must not fire
        var analyzed = 0
        var applied = 0
        val ext = object : AsmExtensionFactory {
            override val perClass get() = true
            override fun create(ctx: xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext) =
                object : AsmExtension {
                    override fun analyze(node: ClassNode) { analyzed++ }
                    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) { applied++ }
                }
        }
        remapJar(Options(extensions = BuiltinAsmExtensions.defaults() + ext))
        assertEquals(0, analyzed)
        assertEquals(1, applied)
    }

    @Test
    fun nonPerClassExtensionGetsFullLifecycle() {
        val events = mutableListOf<String>()
        val ext = AsmExtensionFactory { object : AsmExtension {
            override fun analyze(node: ClassNode) { events += "analyze" }
            override fun finishAnalysis() { events += "finishAnalysis" }
            override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) { events += "postApply" }
        } }
        remapJar(Options(extensions = BuiltinAsmExtensions.defaults() + ext))
        assertEquals(listOf("analyze", "finishAnalysis", "postApply"), events)
    }

    @Test
    fun perClassResourceHooksRunOnStreamingPath() {
        val ext = object : AsmExtensionFactory {
            override val perClass get() = true
            override fun create(ctx: xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext) =
                object : AsmExtension {
                    override fun transformResource(path: String, bytes: ByteArray): ByteArray? =
                        if (path == "data.txt") "CHANGED".toByteArray() else null
                    override fun resources(): List<Pair<String, ByteArray>> =
                        listOf("extra.txt" to "NEW".toByteArray())
                }
        }
        val out = remapJar(
            Options(extensions = BuiltinAsmExtensions.defaults() + ext),
            mapOf("data.txt" to "ORIGINAL".toByteArray()),
        )
        val entries = readEntries(out)
        assertTrue("extra.txt" in entries && "data.txt" in entries)
        ZipFile(out.toFile()).use { zin ->
            assertEquals("CHANGED", zin.getInputStream(zin.getEntry("data.txt")).readBytes().decodeToString())
            assertEquals("NEW", zin.getInputStream(zin.getEntry("extra.txt")).readBytes().decodeToString())
        }
    }

}
