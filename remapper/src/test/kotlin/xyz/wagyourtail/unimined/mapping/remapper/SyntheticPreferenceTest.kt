package xyz.wagyourtail.unimined.mapping.remapper

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.GraphNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desc-less member refs (mixin `method=` strings) resolve against the scanned
 * jar, where javac's generated members — `ACC_BRIDGE` overloads like
 * `channelRead0(ctx,Object)` delegating to `channelRead0(ctx,Packet)` — share
 * the declaration's name. The annotation processor's element model never sees
 * synthetics, so the canonical answer is the declared method: candidates sort
 * declared-first, then all-synthetic matches (lambdas) still resolve when
 * nothing else shares the name.
 */
class SyntheticPreferenceTest {

    private val intermediary = Namespace("intermediary")
    private val mojmap = Namespace("mojmap")

    private fun tree(): MutableMappingTree {
        val tree = MutableMappingTree(setOf(intermediary, mojmap))
        val conn = tree.clazz(
            mapOf(
                intermediary to InternalName.unchecked("net/minecraft/class_2535"),
                mojmap to InternalName.unchecked("net/minecraft/network/Connection"),
            )
        )
        conn.method(
            mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("method_10770;(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/class_2596;)V"),
                mojmap to MethodNameAndDescriptor.unchecked("channelRead0;(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"),
            )
        )
        conn.method(
            mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("method_10771;(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"),
                mojmap to MethodNameAndDescriptor.unchecked("channelRead0;(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V"),
            )
        )
        conn.method(
            mapOf(
                intermediary to MethodNameAndDescriptor.unchecked("method_9000;()V"),
                mojmap to MethodNameAndDescriptor.unchecked("lambda\$tick\$0;()V"),
            )
        )
        return tree
    }

    private fun classBytes(name: String, methods: List<MethodNode>): ByteArray {
        val node = ClassNode().apply {
            this.access = Opcodes.ACC_PUBLIC
            this.name = name
            this.superName = "java/lang/Object"
            this.version = Opcodes.V17
            this.methods = methods
        }
        return ClassWriter(0).also { node.accept(it) }.toByteArray()
    }

    /** javac member order: the declared method first, the bridge it delegates to second */
    private fun scanJar(bridgeFirst: Boolean): java.nio.file.Path {
        val real = MethodNode(Opcodes.ACC_PUBLIC, "channelRead0", "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", null, null)
        val bridge = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE, "channelRead0", "(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V", null, null)
        val lambda = MethodNode(Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC, "lambda\$tick\$0", "()V", null, null)
        val methods = if (bridgeFirst) listOf(bridge, real, lambda) else listOf(real, bridge, lambda)
        val dir = createTempDirectory("conn-jar")
        val jar = dir.resolve("mc.jar")
        jar.outputStream().use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.putNextEntry(ZipEntry("net/minecraft/network/Connection.class"))
                zos.write(classBytes("net/minecraft/network/Connection", methods))
                zos.closeEntry()
            }
        }
        return jar
    }

    private fun remapperFor(jar: java.nio.file.Path): GraphNamespaceRemapper {
        val tree = tree()
        val index = LazyASMInheritanceTree.index(setOf(jar))
        val inheritance = LazyASMInheritanceTree(mojmap, ASMInheritanceTree.ScanData(emptyMap()), index)
        val groups = MemberGroups(inheritance)
        groups.propagate(tree)
        return GraphNamespaceRemapper(tree, inheritance, groups, mojmap, intermediary)
    }

    @Test
    fun `declared method wins over bridge`() {
        val mapped = remapperFor(scanJar(bridgeFirst = false))
            .mapMethod(
                InternalName.unchecked("net/minecraft/network/Connection"),
                UnqualifiedName.unchecked("channelRead0"),
                null
            )
        assertEquals("method_10770", mapped.value)
    }

    @Test
    fun `declared method wins even when bridge scans first`() {
        val mapped = remapperFor(scanJar(bridgeFirst = true))
            .mapMethod(
                InternalName.unchecked("net/minecraft/network/Connection"),
                UnqualifiedName.unchecked("channelRead0"),
                null
            )
        assertEquals("method_10770", mapped.value)
    }

    @Test
    fun `synthetic-only name still resolves`() {
        val mapped = remapperFor(scanJar(bridgeFirst = false))
            .mapMethod(
                InternalName.unchecked("net/minecraft/network/Connection"),
                UnqualifiedName.unchecked("lambda\$tick\$0"),
                null
            )
        assertEquals("method_9000", mapped.value)
    }

}
