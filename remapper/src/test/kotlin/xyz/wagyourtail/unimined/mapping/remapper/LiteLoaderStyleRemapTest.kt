package xyz.wagyourtail.unimined.mapping.remapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.voidMethod
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * LaunchWrapper-era mixin jars (LiteLoader, old sponge coremods) name their
 * configs `mixins.<name>.json` rather than `<name>.mixins.json`, compile
 * *some* annotation literals in a dev namespace while the classes stay
 * obfuscated, and ship a refmap whose `mappings` values point at the
 * obfuscated namespace. Remapping such a jar into a dev namespace must:
 * detect the config by its alternate name, merge the shipped refmap with
 * values remapped into the target namespace, and leave cross-namespace
 * `data` sections (e.g. `searge` for prod launches) verbatim.
 */
class LiteLoaderStyleRemapTest {

    private val dir = createTempDirectory("liteloader-style")

    private fun tree() = treeOf().apply {
        cls("bib", "net/minecraft/client/Minecraft").apply {
            method("aq;()V", "init;()V")
            method("an;()V", "clickMouse;()V")
        }
        cls("blk", "net/minecraft/client/settings/KeyBinding").apply {
            field("d;Ljava/util/Map;", "field_193627_d;Ljava/util/Map;")
        }
        // `oq` declares no `q` — posY is inherited from Entity (`vg`), so
        // resolving `Loq;q:D` needs the scanned supertype chain
        cls("oq", "net/minecraft/entity/player/EntityPlayerMP")
        cls("vg", "net/minecraft/entity/Entity").apply {
            field("q;D", "field_70163_u;D")
        }
    }

    /** Liteloader-shaped mixin: class-refs obf, member literals mcp-dev. */
    private fun mixinBytes() = classBytes("com/mumfrey/liteloader/client/mixin/MixinMinecraft") {
        val mixin = visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true) as AnnotationNode
        mixin.values = listOf("value", listOf(org.objectweb.asm.Type.getObjectType("bib")))
        methods.add(voidMethod("handler\$init").apply {
            val inject = visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true) as AnnotationNode
            inject.values = listOf(
                "method", "init()V",
                "at", AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;").apply {
                    values = listOf("value", "INVOKE", "target", "Lnet/minecraft/client/Minecraft;clickMouse()V")
                },
            )
        })
    }

    /** Accessor-interface mixin — refmap carries an ownerless obf field ref.
     *  `@Mixin` lands in RuntimeInvisibleAnnotations, matching real jars. */
    private fun accessorMixinBytes() = accessorMixinBytes(
        "com/mumfrey/liteloader/client/mixin/IKeyBinding", "blk")

    /** [accessorMixinBytes] with the `@Mixin` target in a chosen namespace —
     *  dependency-transform second passes (modArtifacts) receive a jar whose
     *  literals are already target-named. */
    private fun accessorMixinBytes(mixinName: String, targetName: String) =
        classBytes(mixinName) {
            val mixin = visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false) as AnnotationNode
            mixin.values = listOf("value", listOf(org.objectweb.asm.Type.getObjectType(targetName)))
            methods.add(voidMethod("getCategoryOrder").apply {
                visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true)
            })
        }

    private fun writeModJar(path: Path) {
        writeJar(path, mapOf(
            "bib.class" to classBytes("bib"),
            "blk.class" to classBytes("blk"),
            "com/mumfrey/liteloader/client/mixin/MixinMinecraft.class" to mixinBytes(),
            "com/mumfrey/liteloader/client/mixin/IKeyBinding.class" to accessorMixinBytes(),
            "mixins.liteloader.client.json" to """{
                "package": "com.mumfrey.liteloader.client.mixin",
                "refmap": "mixins.liteloader.client.refmap.json",
                "mixins": ["MixinMinecraft"]
            }""".toByteArray(),
            // shipped refmap: `mappings` is dev-name → obf; `data.searge`
            // is dev-name → searge (for the prod-runtime direction)
            "mixins.liteloader.client.refmap.json" to """{"mappings":{
                "com/mumfrey/liteloader/client/mixin/MixinMinecraft":{
                    "init()V": "Lbib;aq()V"
                },
                "com/mumfrey/liteloader/client/mixin/IKeyBinding":{
                    "CATEGORY_ORDER": "d:Ljava/util/Map;"
                }},
                "data":{"searge":{
                    "com/mumfrey/liteloader/client/mixin/MixinMinecraft":{
                        "init()V": "Lnet/minecraft/client/Minecraft;func_71384_a()V"
                    }
                }}
            }""".toByteArray(),
        ))
    }

    private fun remap(): Path {
        val input = dir.resolve("liteloader.jar")
        val out = dir.resolve("liteloader-named.jar")
        writeModJar(input)
        AsmRemapper.remap(input, out, tree(), obf, named, Options(
            extensions = BuiltinAsmExtensions.defaults() +
                AsmExtensionFactory { ctx -> MixinExtension(ctx) },
        ))
        return out
    }

    /**
     * The modArtifacts shape: a first dep-remap already produced a
     * target-named jar (`@Mixin` literals target-space, MC classes
     * target-named) while the shipped refmap still carries obf values; the
     * second pass then runs an identity outer remap (`named` → `named`)
     * with the extension ctx still bound `obf` → `named`.
     */
    private fun writeAlreadyNamedModJar(path: Path) {
        writeJar(path, mapOf(
            "net/minecraft/client/Minecraft.class" to
                classBytes("net/minecraft/client/Minecraft"),
            "net/minecraft/client/settings/KeyBinding.class" to
                classBytes("net/minecraft/client/settings/KeyBinding"),
            "com/mumfrey/liteloader/client/mixin/MixinMinecraft.class" to
                mixinBytes(),
            "com/mumfrey/liteloader/client/mixin/IKeyBinding.class" to accessorMixinBytes(
                "com/mumfrey/liteloader/client/mixin/IKeyBinding",
                "net/minecraft/client/settings/KeyBinding"),
            "mixins.liteloader.client.json" to """{
                "package": "com.mumfrey.liteloader.client.mixin",
                "refmap": "mixins.liteloader.client.refmap.json",
                "mixins": ["MixinMinecraft"]
            }""".toByteArray(),
            "mixins.liteloader.client.refmap.json" to """{"mappings":{
                "com/mumfrey/liteloader/client/mixin/MixinMinecraft":{
                    "init()V": "Lbib;aq()V"
                },
                "com/mumfrey/liteloader/client/mixin/IKeyBinding":{
                    "CATEGORY_ORDER": "d:Ljava/util/Map;"
                },
                "com/mumfrey/liteloader/client/mixin/MixinNetHandlerPlayServer":{
                    "Lnet/minecraft/entity/player/EntityPlayerMP;posY:D": "Loq;q:D"
                }}
            }""".toByteArray(),
        ))
    }

    /**
     * The source-namespace "classpath" jar — in the real dep transform the
     * index is built over the *source*-namespace MC artifact, so entries
     * carry obf names: `oq` extending `vg` supplies the `posY` chain.
     */
    private fun writeClasspathJar(path: Path) {
        writeJar(path, mapOf(
            "oq.class" to classBytes("oq") { superName = "vg" },
            "vg.class" to classBytes("vg") {
                fields.add(org.objectweb.asm.tree.FieldNode(
                    Opcodes.ACC_PUBLIC, "q", "D", null, null))
            },
        ))
    }

    private fun remapAlreadyNamed(): Path {
        val input = dir.resolve("liteloader-already-named.jar")
        val out = dir.resolve("liteloader-already-named-out.jar")
        val mcJar = dir.resolve("mc-obf.jar")
        writeAlreadyNamedModJar(input)
        writeClasspathJar(mcJar)
        // mirror UmlModArtifactsStepType: merged view emitted through an
        // InheritanceBackedMappingTree over a lazy source-namespace scan
        val merged = MutableMappingTree(setOf(obf, named))
        tree().emit(merged)
        val inputScan = ASMInheritanceTree.scan(setOf(input))
        val overlaid = AsmRemapper.overlay(
            merged,
            LazyASMInheritanceTree(
                obf, inputScan, LazyASMInheritanceTree.index(setOf(mcJar))),
        )
        AsmRemapper.writeJar(out, AsmRemapper.remap(
            input,
            overlaid.remapper(named, named),
            overlaid, obf, named,
            Options(extensions = listOf(
                AsmExtensionFactory { ctx -> MixinExtension(ctx) })),
        ))
        return out
    }

    @Test
    fun `liteloader-style config is detected`() {
        val out = remap()
        assertTrue("mixins.liteloader.client.json" in readEntries(out))
    }

    @Test
    fun `shipped refmap values are remapped into the target namespace`() {
        val out = remap()
        ZipFile(out.toFile()).use { zin ->
            val entry = zin.getEntry("mixins.liteloader.client.refmap.json")
            assertNotNull(entry, "shipped refmap must survive the remap")
            val json = Json.parseToJsonElement(
                zin.getInputStream(entry).readBytes().decodeToString()
            ).jsonObject
            val mixin = json.getValue("mappings").jsonObject
                .getValue("com/mumfrey/liteloader/client/mixin/MixinMinecraft").jsonObject
            // obf ref rewritten to the target namespace — at runtime mixin
            // resolves this against the dev-named classpath directly
            assertEquals(
                "Lnet/minecraft/client/Minecraft;init()V",
                mixin.getValue("init()V").jsonPrimitive.content
            )
        }
    }

    @Test
    fun `ownerless shipped refs resolve against the mixin target`() {
        val out = remap()
        ZipFile(out.toFile()).use { zin ->
            val json = Json.parseToJsonElement(
                zin.getInputStream(zin.getEntry("mixins.liteloader.client.refmap.json"))
                    .readBytes().decodeToString()
            ).jsonObject
            val accessor = json.getValue("mappings").jsonObject
                .getValue("com/mumfrey/liteloader/client/mixin/IKeyBinding").jsonObject
            // bare `d` → resolved on @Mixin target `blk` → mcp dev name
            assertEquals(
                "Lnet/minecraft/client/settings/KeyBinding;field_193627_d:Ljava/util/Map;",
                accessor.getValue("CATEGORY_ORDER").jsonPrimitive.content
            )
        }
    }

    @Test
    fun `ownerless refs resolve when the input jar is already target-named`() {
        val out = remapAlreadyNamed()
        ZipFile(out.toFile()).use { zin ->
            val json = Json.parseToJsonElement(
                zin.getInputStream(zin.getEntry("mixins.liteloader.client.refmap.json"))
                    .readBytes().decodeToString()
            ).jsonObject
            val mappings = json.getValue("mappings").jsonObject
            // the shipped `Lbib;` owner still resolves through the tree
            val mixin = mappings.getValue("com/mumfrey/liteloader/client/mixin/MixinMinecraft").jsonObject
            assertEquals(
                "Lnet/minecraft/client/Minecraft;init()V",
                mixin.getValue("init()V").jsonPrimitive.content
            )
            // `@Mixin` literal arrived target-named — the hint must be
            // normalized to fns before member lookup
            val accessor = mappings.getValue("com/mumfrey/liteloader/client/mixin/IKeyBinding").jsonObject
            assertEquals(
                "Lnet/minecraft/client/settings/KeyBinding;field_193627_d:Ljava/util/Map;",
                accessor.getValue("CATEGORY_ORDER").jsonPrimitive.content
            )
            // `posY` is declared on `oq`'s ancestor `vg` — resolution walks
            // the scanned supertype chain to the declaring row
            val nps = mappings.getValue("com/mumfrey/liteloader/client/mixin/MixinNetHandlerPlayServer").jsonObject
            assertEquals(
                "Lnet/minecraft/entity/player/EntityPlayerMP;field_70163_u:D",
                nps.getValue("Lnet/minecraft/entity/player/EntityPlayerMP;posY:D").jsonPrimitive.content
            )
        }
    }

    @Test
    fun `cross-namespace data sections pass through verbatim`() {
        val out = remap()
        ZipFile(out.toFile()).use { zin ->
            val json = Json.parseToJsonElement(
                zin.getInputStream(zin.getEntry("mixins.liteloader.client.refmap.json"))
                    .readBytes().decodeToString()
            ).jsonObject
            val searge = json.getValue("data").jsonObject
                .getValue("searge").jsonObject
                .getValue("com/mumfrey/liteloader/client/mixin/MixinMinecraft").jsonObject
            // searge names can't resolve under an obf→named remap and must
            // stay verbatim — that table is read by prod-runtime launches
            assertEquals(
                "Lnet/minecraft/client/Minecraft;func_71384_a()V",
                searge.getValue("init()V").jsonPrimitive.content
            )
        }
    }
}
