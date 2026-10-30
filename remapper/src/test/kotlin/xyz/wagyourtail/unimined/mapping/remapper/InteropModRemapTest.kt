package xyz.wagyourtail.unimined.mapping.remapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.tree.AnnotationNode
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.voidMethod
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-toolchain interop acceptance (unimined P9): a loom-shaped
 * intermediary mod jar through the jar remapper — class/member remap,
 * mixin `@At` target rewrite, shipped refmap honored and re-emitted,
 * `META-INF/jars` nested jar kept intact, loader metadata passed through.
 */
class InteropModRemapTest {

    private val dir = createTempDirectory("interop")

    private fun tree() = treeOf().apply {
        cls("net/minecraft/class_1792", "net/minecraft/item/Item").apply {
            method("method_7890;()V", "getItem;()V")
            method("method_7891;()V", "createItem;()V")
        }
    }

    /** `net/fabricmc/example/mixin/ItemMixin` targeting the intermediary item class. */
    private fun mixinBytes() = classBytes("net/fabricmc/example/mixin/ItemMixin") {
        val mixin = visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true) as AnnotationNode
        mixin.values = listOf("value", listOf(org.objectweb.asm.Type.getObjectType("net/minecraft/class_1792")))
        methods.add(voidMethod("inject").apply {
            val inject = visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true) as AnnotationNode
            inject.values = listOf(
                "method", "method_7890()V",
                "at", AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;").apply {
                    values = listOf("value", "INVOKE", "target", "Lnet/minecraft/class_1792;method_7891()V")
                },
            )
        })
    }

    private fun nestedJarBytes(): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("nested_marker.txt"))
            zip.write("nested".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    /** Faithful loom artifact: intermediary classes + fabric metadata + shipped refmap + JiJ. */
    private fun writeModJar(path: Path) {
        writeJar(path, mapOf(
            "net/minecraft/class_1792.class" to classBytes("net/minecraft/class_1792"),
            "net/fabricmc/example/mixin/ItemMixin.class" to mixinBytes(),
            "examplemod.mixins.json" to """{
                "package": "net.fabricmc.example.mixin",
                "refmap": "examplemod-refmap.json",
                "mixins": ["ItemMixin"]
            }""".toByteArray(),
            "examplemod-refmap.json" to """{"mappings":{"net/fabricmc/example/mixin/ItemMixin":{
                "method_7890()V": "Lnet/minecraft/class_1792;method_7890()V"
            }}}""".toByteArray(),
            "fabric.mod.json" to """{"id": "examplemod"}""".toByteArray(),
            "META-INF/jars/nested.jar" to nestedJarBytes(),
        ))
    }

    private fun remap(): Pair<Path, Path> {
        val input = dir.resolve("mod-intermediary.jar")
        val out = dir.resolve("mod-named.jar")
        writeModJar(input)
        AsmRemapper.remap(input, out, tree(), obf, named, Options(
            extensions = BuiltinAsmExtensions.defaults() +
                AsmExtensionFactory { ctx -> MixinExtension(ctx) },
        ))
        return input to out
    }

    @Test
    fun `intermediary classes and member refs land in named`() {
        val (_, out) = remap()
        val entries = readEntries(out)
        assertTrue("net/minecraft/item/Item.class" in entries)
        assertTrue("net/minecraft/class_1792.class" !in entries)

        val mixin = readClasses(out).getValue("net/fabricmc/example/mixin/ItemMixin")
        val inject = mixin.methods.single { it.name == "inject" }
            .visibleAnnotations.single()
        val vals = inject.values
        // `method=` is a member name on the mixin target — remapped
        assertEquals("getItem()V", vals[vals.indexOf("method") + 1])
        // nested @At target — `Lowner;name(desc)` fully remapped
        val at = vals[vals.indexOf("at") + 1] as AnnotationNode
        assertEquals(
            "Lnet/minecraft/item/Item;createItem()V",
            at.values[at.values.indexOf("target") + 1]
        )
    }

    @Test
    fun `shipped refmap is honored and re-emitted in the target namespace`() {
        val (_, out) = remap()
        ZipFile(out.toFile()).use { zin ->
            val entry = zin.getEntry("examplemod-refmap.json")
            assertNotNull(entry, "refmap must survive the remap")
            val json = Json.parseToJsonElement(
                zin.getInputStream(entry).readBytes().decodeToString()
            ).jsonObject
            val mappings = json.getValue("mappings").jsonObject
            val mixin = mappings.getValue("net/fabricmc/example/mixin/ItemMixin").jsonObject
            // shipped intermediary ref must now point at the named ref
            assertEquals(
                "Lnet/minecraft/item/Item;getItem()V",
                mixin.getValue("method_7890()V").jsonPrimitive.content
            )
        }
    }

    @Test
    fun `loader metadata and nested jars pass through intact`() {
        val (_, out) = remap()
        val entries = readEntries(out)
        assertTrue("fabric.mod.json" in entries)
        assertTrue("examplemod.mixins.json" in entries)
        assertTrue("META-INF/jars/nested.jar" in entries)

        ZipFile(out.toFile()).use { zin ->
            val nested = zin.getInputStream(zin.getEntry("META-INF/jars/nested.jar"))
                .readBytes()
            ZipFile(out.toFile()).close()
            java.io.ByteArrayInputStream(nested).use { bin ->
                java.util.zip.ZipInputStream(bin).use { nestedZip ->
                    var found = false
                    generateSequence { nestedZip.nextEntry }.forEach {
                        if (it.name == "nested_marker.txt") found = true
                    }
                    assertTrue(found, "nested jar contents must be intact")
                }
            }
        }
    }
}
