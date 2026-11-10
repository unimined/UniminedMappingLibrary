package xyz.wagyourtail.unimined.mapping.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.BuiltinAsmExtensions
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension
import xyz.wagyourtail.unimined.mapping.remapper as nsBoundRemapper
import xyz.wagyourtail.unimined.mapping.sink.emit
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repro for the osl-config regression: `client.MinecraftMixin`'s
 * `@Shadow` field `f_26407825` (calamus `Minecraft.world`) must rename
 * to `world` on the decl *and* inside handler field insns, and the
 * generated refmap must carry the `f_26407825` short key.
 */
class Gen2MixinReproTest {

    private fun node(jar: java.nio.file.Path, internal: String): ClassNode {
        ZipFile(jar.toFile()).use { zf ->
            val e = zf.entries().asSequence().first { it.name == "$internal.class" }
            val cn = ClassNode()
            ClassReader(zf.getInputStream(e).readBytes()).accept(cn, 0)
            return cn
        }
    }

    @Test
    fun shadowFieldRenameConsistency() = runTest {
        val f = java.io.File("/tmp/pmnew/views/uml-tree")
        val modJar = java.io.File(
            "/home/william/.gradle/caches/modules-2/files-2.1/" +
                "net.ornithemc.osl-gen2/config/0.6.3-alpha.1+mcb1.3-mcb1.7.3/" +
                "d1b5225d746afaa1fadd2eba72df67c71964e6c8/" +
                "config-0.6.3-alpha.1+mcb1.3-mcb1.7.3.jar"
        )
        if (!f.exists() || !modJar.exists()) return@runTest
        val tree = UMFReader.read(f.readText())
        val cal = Namespace("calamusGen2")
        val fea = Namespace("featherGen2")
        val out = createTempFile("cfg-fea", ".jar")
        val stage1 = createTempFile("cfg-stage1", ".jar")
        try {
            // stage 1: the upstream uml:remap step — outer calamus->feather
            // (shadow decl names survive; descs + insn refs go feather)
            AsmRemapper.remap(
                modJar.toPath(), stage1, listOf(tree), cal, fea,
                LazyASMInheritanceTree(
                    cal,
                    ASMInheritanceTree.scan(setOf(modJar.toPath())),
                    emptyMap(),
                ),
                Options(extensions = BuiltinAsmExtensions.defaults()),
            )
            // stage 2: uml:modArtifacts — identity outer remap (tns->tns)
            // over the overlaid tree, extensions do the fixups; mirrors
            // UmlModArtifactsStepType exactly
            val merged = xyz.wagyourtail.unimined.mapping.MutableMappingTree(
                tree.namespaces.toSet()
            )
            tree.emit(merged)
            val overlaid = AsmRemapper.overlay(
                merged,
                LazyASMInheritanceTree(
                    cal,
                    ASMInheritanceTree.scan(setOf(stage1)),
                    emptyMap(),
                ),
            )
            AsmRemapper.writeJar(
                out,
                AsmRemapper.remap(
                    stage1,
                    tree.nsBoundRemapper(fea, fea),
                    overlaid,
                    cal,
                    fea,
                    options = Options(
                        extensions = BuiltinAsmExtensions.defaults() +
                            AsmExtensionFactory { ctx -> MixinExtension(ctx) },
                    ),
                ),
            )
            val mx = node(out, "net/ornithemc/osl/config/impl/mixin/client/MinecraftMixin")
            println("fields: ${mx.fields.map { it.name + ":" + it.desc }}")
            val world = mx.fields.firstOrNull { it.name == "f_26407825" || it.name == "world" }
            println("shadow field: ${world?.name}:${world?.desc}")
            assertEquals("world", world?.name)
            // the handler's field access must follow the decl rename
            val frefs = mx.methods.flatMap { m ->
                m.instructions.toArray().toList()
                    .filterIsInstance<org.objectweb.asm.tree.FieldInsnNode>()
                    .map { "${it.owner}.${it.name}" }
            }
            println("field refs: $frefs")
            assertEquals(false, frefs.any { it.endsWith(".f_26407825") })
            // refmap resource
            ZipFile(out.toFile()).use { zf ->
                val rm = zf.entries().asSequence().firstOrNull { it.name.endsWith("refmap.json") }
                println("refmap: ${rm?.name}")
                rm?.let {
                    val txt = zf.getInputStream(it).readBytes().decodeToString()
                    println(txt.take(600))
                }
            }
        } finally {
            out.deleteIfExists()
            stage1.deleteIfExists()
        }
    }
}
