package xyz.wagyourtail.unimined.mapping.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.envView
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.sink.emit
import java.util.zip.ZipFile
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repro for the b1.7.3 gen2 merge misses: `wt.a(ei;List)` /
 * `uu.a(fd;III;eq;ArrayList)` stay official-named through the
 * official:client→calamusGen2 merge remap even though both envViews
 * resolve the rows. Exercises the real AsmRemapper path (scan +
 * MemberGroups + graph remap) on the real client jar + merged tree.
 */
class Gen2RealRemapReproTest {

    private fun node(jar: java.nio.file.Path, internal: String): ClassNode {
        ZipFile(jar.toFile()).use { zf ->
            val e = zf.entries().asSequence().first { it.name == "$internal.class" }
            val cn = ClassNode()
            ClassReader(zf.getInputStream(e).readBytes()).accept(cn, 0)
            return cn
        }
    }

    @Test
    fun clientSideMergeRemap() = runTest {
        val f = java.io.File("/tmp/pmnew/views/uml-tree")
        val clientJar = java.io.File(
            "/home/william/.gradle/caches/unimined/native-cache/versions/b1.7.3/client.jar"
        )
        if (!f.exists() || !clientJar.exists()) return@runTest
        val tree = UMFReader.read(f.readText())
        val view = tree.envView("client")
        val off = Namespace("official")
        val cal = Namespace("calamusGen2")
        // direct tree-level lookup on this payload
        run {
            val cls = view.getClass(off, xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked("wt"))
            println("wt class: ${cls?.names}")
            val ms = cls?.methods ?: emptyList()
            for (m in ms) println("  row names=${m.names} descs=${m.descs}")
            with(view) {
                val hit = cls?.getMethod(
                    off,
                    xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("a"),
                    xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor.unchecked("(Lei;Ljava/util/List;)V"),
                )
                println("direct a(ei;List) -> ${hit?.names}")
            }
            // what mergeTrees produces — emit view into a MutableMappingTree
            val merged = xyz.wagyourtail.unimined.mapping.MutableMappingTree(view.namespaces)
            view.emit(merged)
            val mcls = merged.getClass(off, xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked("wt"))
            println("merged wt: ${mcls?.names}")
            for (m in mcls?.methods ?: emptyList()) println("  merged row names=${m.names} descs=${m.descs}")
            with(merged) {
                val hit = mcls?.getMethod(
                    off,
                    xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("a"),
                    xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor.unchecked("(Lei;Ljava/util/List;)V"),
                )
                println("merged a(ei;List) -> ${hit?.names}")
            }
        }
        val out = createTempFile("client-cal", ".jar")
        try {
            AsmRemapper.remap(
                clientJar.toPath(), out, listOf(view), off, cal,
                LazyASMInheritanceTree(
                    off,
                    ASMInheritanceTree.scan(setOf(clientJar.toPath())),
                    emptyMap(),
                ),
                Options(),
            )
            val wt = node(out, "net/minecraft/unmapped/C_08489468")
            val names = wt.methods.associate { it.desc to it.name }
            println("wt members: $names")
            assertEquals("m_92317641", names["(Lnet/minecraft/unmapped/C_64676926;Ljava/util/List;)V"])
            val uu = node(out, "net/minecraft/unmapped/C_81592558")
            val uuNames = uu.methods.associate { it.desc to it.name }
            assertEquals(
                "m_64404227",
                uuNames["(Lnet/minecraft/unmapped/C_64041439;IIILnet/minecraft/unmapped/C_82690114;Ljava/util/ArrayList;)V"],
            )
        } finally {
            out.deleteIfExists()
        }
    }

    @Test
    fun serverSideMergeRemap() = runTest {
        val f = java.io.File("/tmp/pmnew/views/uml-tree")
        val serverJar = java.io.File(
            "/home/william/.gradle/caches/unimined/native-cache/versions/b1.7.3/server.jar"
        )
        if (!f.exists() || !serverJar.exists()) return@runTest
        val tree = UMFReader.read(f.readText())
        val view = tree.envView("server")
        val off = Namespace("official")
        val cal = Namespace("calamusGen2")
        val out = createTempFile("server-cal", ".jar")
        try {
            AsmRemapper.remap(
                serverJar.toPath(), out, listOf(view), off, cal,
                LazyASMInheritanceTree(
                    off,
                    ASMInheritanceTree.scan(setOf(serverJar.toPath())),
                    emptyMap(),
                ),
                Options(),
            )
            val om = node(out, "net/minecraft/unmapped/C_08489468")
            val names = om.methods.associate { it.desc to it.name }
            println("om members: $names")
            assertEquals("m_92317641", names["(Lnet/minecraft/unmapped/C_64676926;Ljava/util/List;)V"])
        } finally {
            out.deleteIfExists()
        }
    }
}
