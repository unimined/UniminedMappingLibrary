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
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.impl.fillMissingNames
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Composed mapping bundles fill a member's missing target-ns name from the
 * parent hop ([fillMissingNames]) — e.g. an impl class whose method
 * intermediary leaves unmapped gets the official obf name (`a`) written into
 * its `intermediary` slot. When that impl is unioned into the same
 * [MemberGroups] component as the interface declaration that *does* carry a
 * real intermediary name, the filled `a` must not win: previously it counted
 * as a genuine rename (it differs from the fns `is`) and `minBy` picked it
 * over `method_40225`, so prod remaps emitted obf names for every
 * parchment-documented/inherited member — repro of the baritone prod-jar
 * failure.
 */
class ComposeParchmentReproTest {

    private val official = Namespace("official")
    private val intermediary = Namespace("intermediary")
    private val mojmap = Namespace("mojmap")

    /**
     * The composed-tree shape unimined produces for `Holder.is` +
     * `Holder$Reference.is`: the interface row carries all three names; the
     * impl row lacks `intermediary` (intermediary maps members at their
     * declaration origin only) so [fillMissingNames] copies official `a`
     * into that slot.
     */
    private fun composed(): MutableMappingTree {
        val tree = MutableMappingTree(setOf(official, intermediary, mojmap))
        val holder = tree.clazz(
            mapOf(
                official to InternalName.unchecked("jr"),
                intermediary to InternalName.unchecked("net/minecraft/class_6880"),
                mojmap to InternalName.unchecked("net/minecraft/core/Holder"),
            )
        )
        holder.method(
            mapOf(
                official to MethodNameAndDescriptor.unchecked("a;(Laku;)Z"),
                intermediary to MethodNameAndDescriptor.unchecked("method_40225;(Lnet/minecraft/class_5321;)Z"),
                mojmap to MethodNameAndDescriptor.unchecked("is;(Lnet/minecraft/resources/ResourceKey;)Z"),
            )
        )
        val ref = tree.clazz(
            mapOf(
                official to InternalName.unchecked("jr\$c"),
                intermediary to InternalName.unchecked("net/minecraft/class_6880\$class_6883"),
                mojmap to InternalName.unchecked("net/minecraft/core/Holder\$Reference"),
            )
        )
        ref.method(
            mapOf(
                official to MethodNameAndDescriptor.unchecked("a;(Laku;)Z"),
                mojmap to MethodNameAndDescriptor.unchecked("is;(Lnet/minecraft/resources/ResourceKey;)Z"),
            )
        )
        tree.fillMissingNames(official to setOf(intermediary), official to setOf(mojmap))
        return tree
    }

    private fun classBytes(name: String, access: Int, ifaces: List<String>, methods: List<MethodNode>): ByteArray {
        val node = ClassNode().apply {
            this.access = access
            this.name = name
            this.superName = "java/lang/Object"
            this.interfaces = ifaces
            this.version = Opcodes.V17
            this.methods = methods
        }
        return ClassWriter(0).also { node.accept(it) }.toByteArray()
    }

    /**
     * A fake mojmap-side jar: `Holder` declares `is` (abstract interface),
     * `Holder$Reference` implements it and re-declares the method — the
     * impl's member unions into the same override component.
     */
    private fun mojmapJar(): java.nio.file.Path {
        val dir = createTempDirectory("mojmap-jar")
        val jar = dir.resolve("mc.jar")
        jar.outputStream().use { fos ->
            ZipOutputStream(fos).use { zos ->
                fun put(name: String, bytes: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
                put(
                    "net/minecraft/core/Holder.class",
                    classBytes(
                        "net/minecraft/core/Holder",
                        Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
                        emptyList(),
                        listOf(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "is", "(Lnet/minecraft/resources/ResourceKey;)Z", null, null))
                    )
                )
                put(
                    "net/minecraft/core/Holder\$Reference.class",
                    classBytes(
                        "net/minecraft/core/Holder\$Reference",
                        Opcodes.ACC_PUBLIC,
                        listOf("net/minecraft/core/Holder"),
                        listOf(MethodNode(Opcodes.ACC_PUBLIC, "is", "(Lnet/minecraft/resources/ResourceKey;)Z", null, null))
                    )
                )
            }
        }
        return jar
    }

    @Test
    fun `hop-filled impl name does not outrank interface rename`() {
        val tree = composed()

        // sanity: the fill produced the poisoned cell this test defends against
        val impl = tree.classes.first { it.names[mojmap].toString() == "net/minecraft/core/Holder\$Reference" }
        val implIs = impl.methods.first { it.names[mojmap].toString() == "is" }
        assertEquals("a", implIs.names[intermediary].toString())

        val jar = mojmapJar()
        val index = LazyASMInheritanceTree.index(setOf(jar))
        val inheritance = LazyASMInheritanceTree(mojmap, ASMInheritanceTree.ScanData(emptyMap()), index)
        val groups = MemberGroups(inheritance)
        groups.propagate(tree)
        val remapper = GraphNamespaceRemapper(tree, inheritance, groups, mojmap, intermediary)

        val desc = MethodDescriptor.unchecked("(Lnet/minecraft/resources/ResourceKey;)Z")
        val name = UnqualifiedName.unchecked("is")

        assertEquals(
            "method_40225",
            remapper.mapMethod(
                InternalName.unchecked("net/minecraft/core/Holder"), name, desc
            ).toString()
        )
        assertEquals(
            "method_40225",
            remapper.mapMethod(
                InternalName.unchecked("net/minecraft/core/Holder\$Reference"), name, desc
            ).toString()
        )
        // desc-less refs (mixin `method=` strings) must take the same
        // inheritance-aware path — the flat fallback reads the impl row's
        // hop-filled `a` verbatim
        assertEquals(
            "method_40225",
            remapper.mapMethod(
                InternalName.unchecked("net/minecraft/core/Holder\$Reference"), name, null
            ).toString()
        )
        inheritance.close()
    }
}
