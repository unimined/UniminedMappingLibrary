package xyz.wagyourtail.unimined.mapping.remapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.tree.AnnotationNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
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
 * Rift-era shipped-refmap bridge: the mod bytecode is obfuscated (official)
 * while member selectors inside nested injection-point annotations stay in
 * the MCP-ish compile namespace (`Lnet/minecraft/resources/ResourcePackList;
 * addPackFinder(Lnet/minecraft/resources/IPackFinder;)V`). The shipped refmap
 * bridges them to official canonical refs (`Lux;a(Luy;)V`).
 *
 * Regression: `MemberRefParts.resolveViaExisting` validated the refmap
 * value's fns-space written descriptor against `resolveMemberDesc`'s
 * *target-space* rendering — the comparison could never hold for any desc
 * carrying a remapped class, so every desc-bearing legacy fallback was
 * rejected and the annotation survived in compile space. (Bare refs like
 * `runTick` → `Lcft;m()V` only bridged because `()V` is class-free.)
 *
 * Per 1.x semantics both emission modes must be correct: `emitRefmap=true`
 * emits target-space refmap values, `emitRefmap=false` rewrites the
 * annotation in place (and neutralizes the shipped refmap).
 */
class RiftRefmapBridgeTest {

    private val dir = createTempDirectory("rift-refmap-bridge")

    private val official = Namespace("official")
    private val feather = Namespace("feather")

    /** MCP-ish compile namespace — never a tree row; only the refmap bridges it */
    private val compileTarget =
        "Lnet/minecraft/resources/ResourcePackList;addPackFinder(Lnet/minecraft/resources/IPackFinder;)V"
    /** feather-space canonical rendering of the official row `ux.a(Luy;)V` */
    private val featherTarget =
        "Lnet/minecraft/resource/pack/repository/PackRepository;addSource(Lnet/minecraft/resource/pack/repository/RepositorySource;)V"

    private val mixinName = "org/dimdev/rift/mixin/hook/client/MixinMinecraft"

    private fun tree() = treeOf(official, feather).apply {
        clazz(mapOf(
            official to InternalName.unchecked("ux"),
            feather to InternalName.unchecked("net/minecraft/resource/pack/repository/PackRepository"),
        )).method(mapOf(
            official to MethodNameAndDescriptor.unchecked("a;(Luy;)V"),
            feather to MethodNameAndDescriptor.unchecked(
                "addSource;(Lnet/minecraft/resource/pack/repository/RepositorySource;)V"),
        ))
        clazz(mapOf(
            official to InternalName.unchecked("uy"),
            feather to InternalName.unchecked("net/minecraft/resource/pack/repository/RepositorySource"),
        ))
        clazz(mapOf(
            official to InternalName.unchecked("cft"),
            feather to InternalName.unchecked("net/minecraft/client/Minecraft"),
        ))
    }

    /** the exact rift hooks shape: class literals obf (`Lcft;`), the nested
     *  `@At(target=…)` string stuck in the compile namespace */
    private fun mixinBytes() = classBytes(mixinName) {
        val mixin = visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false) as AnnotationNode
        mixin.values = listOf("value", listOf(org.objectweb.asm.Type.getObjectType("cft")))
        methods.add(voidMethod("onInit").apply {
            visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;").apply {
                values = listOf(
                    "method", listOf("<init>"),
                    "at", AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;").apply {
                        values = listOf(
                            "value", "INVOKE",
                            "target", compileTarget,
                            "ordinal", 1,
                        )
                    },
                )
            })
        })
    }

    private fun writeModJar(path: Path) {
        writeJar(path, mapOf(
            "$mixinName.class" to mixinBytes(),
            "mixins.rift.hooks.json" to """{
                "package": "org.dimdev.rift.mixin.hook",
                "refmap": "mixins.rift.refmap.json",
                "mixins": ["client.MixinMinecraft"]
            }""".toByteArray(),
            "mixins.rift.refmap.json" to """{"mappings":{
                "$mixinName":{
                    "$compileTarget": "Lux;a(Luy;)V"
                }}
            }""".toByteArray(),
        ))
    }

    private fun remap(emitRefmap: Boolean, tag: String): Path {
        val input = dir.resolve("rift-$tag.jar")
        val out = dir.resolve("rift-$tag-feather.jar")
        writeModJar(input)
        AsmRemapper.remap(input, out, tree(), official, feather, Options(
            extensions = BuiltinAsmExtensions.defaults() +
                AsmExtensionFactory { ctx -> MixinExtension(ctx, emitRefmap = emitRefmap) },
        ))
        return out
    }

    /** extract the nested `@At(target=…)` string off the remapped mixin */
    private fun nestedAtTarget(out: Path): String? {
        val methods = readClasses(out).getValue(mixinName).methods
        val redirect = methods.flatMap { it.visibleAnnotations ?: emptyList() }
            .firstOrNull { it.desc == "Lorg/spongepowered/asm/mixin/injection/Redirect;" }
            ?: return null
        var i = 0
        while (i < redirect.values.size - 1) {
            if (redirect.values[i] == "at") {
                val at = redirect.values[i + 1] as? AnnotationNode
                var j = 0
                while (j < (at?.values?.size ?: 0) - 1) {
                    if (at!!.values[j] == "target") return at.values[j + 1] as? String
                    j += 2
                }
            }
            i += 2
        }
        return null
    }

    @Test
    fun `emitRefmap=true emits target-space refmap values for bridged legacy refs`() {
        val out = remap(emitRefmap = true, tag = "emit")
        ZipFile(out.toFile()).use { zin ->
            val entry = zin.getEntry("mixins.rift.refmap.json")
            assertNotNull(entry, "shipped refmap must survive the remap")
            val json = Json.parseToJsonElement(
                zin.getInputStream(entry).readBytes().decodeToString()
            ).jsonObject
            val mixin = json.getValue("mappings").jsonObject
                .getValue(mixinName).jsonObject
            assertEquals(featherTarget, mixin.getValue(compileTarget).jsonPrimitive.content,
                "refmap must carry the feather-space canonical ref so runtime substitution resolves")
        }
    }

    @Test
    fun `emitRefmap=true rewrites the nested annotation in place as well`() {
        val out = remap(emitRefmap = true, tag = "emit-inplace")
        assertEquals(featherTarget, nestedAtTarget(out),
            "nested @At target must not stay in the compile namespace")
    }

    @Test
    fun `emitRefmap=false rewrites the nested annotation in place`() {
        val out = remap(emitRefmap = false, tag = "noemit")
        assertEquals(featherTarget, nestedAtTarget(out),
            "nested @At target must rewrite to the feather-space canonical ref")
    }

    @Test
    fun `emitRefmap=false neutralizes the shipped refmap`() {
        val out = remap(emitRefmap = false, tag = "noemit-neutral")
        ZipFile(out.toFile()).use { zin ->
            val entry = zin.getEntry("mixins.rift.refmap.json")
            assertNotNull(entry, "shipped refmap file must still exist (emptied)")
            val json = Json.parseToJsonElement(
                zin.getInputStream(entry).readBytes().decodeToString()
            ).jsonObject
            assertTrue(json.getValue("mappings").jsonObject.isEmpty(),
                "emitRefmap=false must empty the refmap so nothing reintroduces compile-space refs")
            val config = Json.parseToJsonElement(
                zin.getInputStream(zin.getEntry("mixins.rift.hooks.json")).readBytes().decodeToString()
            ).jsonObject
            assertTrue("refmap" !in config,
                "the config's refmap key must be stripped so sponge never consults it")
        }
    }
}
