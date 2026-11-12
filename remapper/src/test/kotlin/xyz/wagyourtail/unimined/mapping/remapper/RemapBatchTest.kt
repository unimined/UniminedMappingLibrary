package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.runBlocking
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.HasInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.RemapBatch
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.*

/**
 * Batch parity: [AsmRemapper.prepareBatch] + [RemapBatch.remap] over
 * several jars must produce the same output as the per-invocation
 * `remap` entry point — the merge/propagate work is shared, the
 * semantics are not supposed to change.
 */
class RemapBatchTest {

    private val obf = Namespace("intermediary")
    private val named = Namespace("named")

    private fun classBytes(name: String, access: Int = Opcodes.ACC_PUBLIC, block: ClassNode.() -> Unit = {}): ByteArray {
        val node = ClassNode().apply {
            this.access = access
            this.name = name
            this.superName = "java/lang/Object"
            this.version = Opcodes.V17
            block()
        }
        val writer = org.objectweb.asm.ClassWriter(0)
        node.accept(writer)
        return writer.toByteArray()
    }

    private fun voidMethod(name: String, access: Int = Opcodes.ACC_PUBLIC): MethodNode =
        MethodNode(access, name, "()V", null, null).apply {
            visitCode()
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 1)
            visitEnd()
        }

    private fun callMethod(owner: String, name: String): MethodNode =
        MethodNode(Opcodes.ACC_PUBLIC, "caller${owner.substringAfterLast('/')}$name", "()V", null, null).apply {
            visitCode()
            visitTypeInsn(Opcodes.NEW, owner)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 1)
            visitEnd()
        }

    private fun writeJar(path: Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { jos ->
            for ((name, bytes) in entries) {
                jos.putNextEntry(ZipEntry(name))
                jos.write(bytes)
                jos.closeEntry()
            }
        }
    }

    private fun readClasses(path: Path): Map<String, ClassNode> {
        val out = mutableMapOf<String, ClassNode>()
        ZipFile(path.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().filter { it.name.endsWith(".class") }) {
                val node = ClassNode()
                org.objectweb.asm.ClassReader(zin.getInputStream(entry)).accept(node, 0)
                out[node.name] = node
            }
        }
        return out
    }

    private fun tree(): MutableMappingTree {
        val tree = MutableMappingTree(setOf(obf, named))
        val cls = tree.clazz(
            mapOf(
                obf to InternalName.unchecked("eqm"),
                named to InternalName.unchecked("net/minecraft/Example"),
            )
        )
        cls.field(
            mapOf(
                obf to FieldNameAndDescriptor.unchecked("field_1;I"),
                named to FieldNameAndDescriptor.unchecked("mappedField;I"),
            )
        )
        cls.method(
            mapOf(
                obf to MethodNameAndDescriptor.unchecked("method_1;()V"),
                named to MethodNameAndDescriptor.unchecked("mappedMethod;()V"),
            )
        )
        return tree
    }

    /** class names + member names per class — the semantic content of a
     *  remap output (zip bytes legitimately differ between paths). */
    private fun shape(path: Path): Map<String, List<String>> =
        readClasses(path).mapValues { (_, c) ->
            (c.methods.map { "${it.name}${it.desc}" } + c.fields.map { "${it.name}:${it.desc}" }).sorted()
        }

    @Test
    fun `batch remap matches per-input remap`() = runBlocking {
        val dir = createTempDirectory()
        // mc.jar: the mapped base — eqm's declaration must sit on the
        // classpath/index or the union-find has no ClassInfo to climb
        val mcJar = dir.resolve("mc.jar")
        writeJar(mcJar, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
                methods.add(voidMethod("method_1"))
            },
        ))
        // jarA: subclass of eqm overriding method_1 — propagation must
        // rename the override to mappedMethod via the shared group
        val jarA = dir.resolve("a.jar")
        writeJar(jarA, mapOf(
            "sub/A.class" to classBytes("sub/A") {
                superName = "eqm"
                methods.add(voidMethod("method_1"))
            },
        ))
        // jarB: calls sub/A.method_1 — resolves through the union index
        // (A lives in jarA, not on jarB) into A's group
        val jarB = dir.resolve("b.jar")
        writeJar(jarB, mapOf(
            "sub/B.class" to classBytes("sub/B") {
                methods.add(callMethod("sub/A", "method_1"))
            },
        ))
        val libs = setOf(mcJar, jarA, jarB)

        // reference: per-invocation graph path, one remap per jar
        val refA = dir.resolve("ref-a.jar")
        val refB = dir.resolve("ref-b.jar")
        AsmRemapper.remap(jarA, refA, listOf(tree()), obf, named, libraries = libs)
        AsmRemapper.remap(jarB, refB, listOf(tree()), obf, named, libraries = libs)

        // batch: merge+propagate once over the union index
        val batch = AsmRemapper.prepareBatch(listOf(tree()), obf, named, libraries = libs)
        val outA = dir.resolve("out-a.jar")
        val outB = dir.resolve("out-b.jar")
        batch.use {
            it.remap(jarA, outA, ASMInheritanceTree.scan(setOf(jarA)))
            it.remap(jarB, outB, ASMInheritanceTree.scan(setOf(jarB)))
        }

        assertEquals(shape(refA), shape(outA), "jar A: batch output diverges from per-input remap")
        assertEquals(shape(refB), shape(outB), "jar B: batch output diverges from per-input remap")
        // propagation actually happened (not just identity passthrough)
        val a = readClasses(outA).getValue("sub/A")
        assertTrue(a.methods.any { it.name == "mappedMethod" },
            "sub/A override should propagate to mappedMethod, got ${a.methods.map { it.name }}")
        val b = readClasses(outB).getValue("sub/B")
        val call = b.methods.first { it.name.startsWith("caller") }
        assertTrue(call.instructions.toArray().any {
            it is org.objectweb.asm.tree.MethodInsnNode && it.name == "mappedMethod"
        }, "cross-jar callsite should resolve to mappedMethod through the shared index")
    }

    @Test
    fun `mergeScan is a no-op for index-covered inputs`() = runBlocking {
        val dir = createTempDirectory()
        val jar = dir.resolve("in.jar")
        writeJar(jar, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
                methods.add(voidMethod("method_1"))
            },
        ))
        val batch = AsmRemapper.prepareBatch(listOf(tree()), obf, named, libraries = setOf(jar))
        val out1 = dir.resolve("o1.jar")
        val out2 = dir.resolve("o2.jar")
        batch.use {
            // covered by index: mergeScan with the same jar must not
            // swap materialized ClassInfos out from under the groups
            it.remap(jar, out1, ASMInheritanceTree.scan(setOf(jar)))
            it.remap(jar, out2)
        }
        assertEquals(shape(out1), shape(out2))
        assertTrue(readClasses(out2).containsKey("net/minecraft/Example"))
        Unit
    }

    @Test
    fun `mappingView resolves propagated members without materializing`() = runBlocking {
        val dir = createTempDirectory()
        val mcJar = dir.resolve("mc.jar")
        writeJar(mcJar, mapOf(
            "eqm.class" to classBytes("eqm") {
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
                methods.add(voidMethod("method_1"))
            },
        ))
        // subclass overriding method_1 — lives only in the input scan
        val jarA = dir.resolve("a.jar")
        writeJar(jarA, mapOf(
            "sub/A.class" to classBytes("sub/A") {
                superName = "eqm"
                methods.add(voidMethod("method_1"))
            },
        ))
        val batch = AsmRemapper.prepareBatch(
            listOf(tree()), obf, named, libraries = setOf(mcJar, jarA))
        batch.use {
            val view = it.mappingView(ASMInheritanceTree.scan(setOf(jarA)))
            // the scan-only class surfaces through extraClasses and its
            // override resolves through the batch's own propagated groups
            val a = view.getClass(obf, InternalName.unchecked("sub/A"))
            assertNotNull(a, "scan-only class should surface in the view")
            val m = with(view) {
                a.getMethod(obf,
                    xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("method_1"),
                    xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor.unchecked("()V"))
            }
            assertNotNull(m)
            assertEquals("mappedMethod", m.names[named]?.value)
            // scan-only class rows carry no target class name — the tree
            // only names what mappings named
            assertNull(a.names[named])
            // the shared graph is exposed for scan lookups
            assertTrue((view as HasInheritanceTree).inheritance === it.graph)
        }
    }

    @Test
    fun `extraScans fold a foreign-namespace jar into the graph`() = runBlocking {
        val dir = createTempDirectory()
        // the "official" MC jar — its classes are not in the batch's
        // fns; a scan translated through the modsource bundle supplies
        // the same ClassInfos a remapped jar would carry
        val official = Namespace("official")
        val mcJar = dir.resolve("mc-official.jar")
        writeJar(mcJar, mapOf(
            "mc/Base.class" to classBytes("mc/Base") {
                methods.add(voidMethod("officialMethod"))
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "officialField", "Lmc/Base;", null, null))
            },
            "mc/Sub.class" to classBytes("mc/Sub") { superName = "mc/Base" },
        ))
        val mcTree = MutableMappingTree(setOf(official, obf)).apply {
            val b = clazz(mapOf(
                official to InternalName.unchecked("mc/Base"),
                obf to InternalName.unchecked("eqm"),
            ))
            b.method(mapOf(
                official to MethodNameAndDescriptor.unchecked("officialMethod;()V"),
                obf to MethodNameAndDescriptor.unchecked("method_1;()V"),
            ))
            b.field(mapOf(
                official to FieldNameAndDescriptor.unchecked("officialField;Lmc/Base;"),
                obf to FieldNameAndDescriptor.unchecked("field_1;Leqm;"),
            ))
            clazz(mapOf(
                official to InternalName.unchecked("mc/Sub"),
                obf to InternalName.unchecked("eqx"),
            ))
        }
        // the elision primitive — scan + rename instead of a jar remap
        val translated = ASMInheritanceTree.scan(setOf(mcJar))
            .renamed(mcTree.remapper(official, obf))
        assertTrue(translated.classes.containsKey(InternalName.unchecked("eqm")))
        assertFalse(translated.classes.containsKey(InternalName.unchecked("mc/Base")))
        // super refs, member names and descriptor class refs all renamed
        assertEquals(InternalName.unchecked("eqm"),
            translated.classes.getValue(InternalName.unchecked("eqx")).superType)
        val base = translated.classes.getValue(InternalName.unchecked("eqm"))
        assertTrue(base.methods.any { it.name.value == "method_1" },
            "member names should rename through the modsource bundle")
        val fld = base.fields.single()
        assertEquals("field_1", fld.name.value)
        assertEquals(xyz.wagyourtail.unimined.mapping.jvms.four.three.two
            .FieldDescriptor.unchecked("Leqm;"), fld.descriptor)

        // a mod jar extending the intermediary-named MC class
        val modJar = dir.resolve("mod.jar")
        writeJar(modJar, mapOf(
            "sub/A.class" to classBytes("sub/A") {
                superName = "eqm"
                methods.add(voidMethod("method_1"))
            },
        ))
        val batch = AsmRemapper.prepareBatch(
            listOf(tree()), obf, named,
            extraScans = listOf(translated)
        )
        val out = dir.resolve("out.jar")
        batch.use { it.remap(modJar, out, ASMInheritanceTree.scan(setOf(modJar))) }
        val a = readClasses(out).getValue("sub/A")
        assertTrue(a.methods.any { it.name == "mappedMethod" },
            "override of eqm.method_1 should propagate through the " +
                "translated MC scan, got ${a.methods.map { it.name }}")
    }

    @Test
    fun `input scan stamps rows for classes absent from classpath index`() = runBlocking {
        val dir = createTempDirectory()
        // env-artifact shape: the input jar itself carries the mapped
        // base (eqm.method_1) AND an unmapped subclass override — with
        // the input absent from the classpath index, the batch-wide
        // propagate ran before mergeScan and never stamped eqm's
        // component, so the override used to keep its source name
        // (cleanroom: forge-side NamespacedDefaultedWrapper.a stayed
        // obfuscated while the MC superclass renamed to func_*)
        val input = dir.resolve("in.jar")
        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(voidMethod("method_1"))
            },
            "sub/A.class" to classBytes("sub/A") {
                superName = "eqm"
                methods.add(voidMethod("method_1"))
            },
        ))
        val batch = AsmRemapper.prepareBatch(
            listOf(tree()), obf, named, libraries = emptySet())
        val out = dir.resolve("out.jar")
        batch.use { it.remap(input, out, ASMInheritanceTree.scan(setOf(input))) }
        val classes = readClasses(out)
        assertTrue(classes.containsKey("net/minecraft/Example"),
            "eqm should rename through its explicit row")
        val a = classes.getValue("sub/A")
        assertTrue(a.methods.any { it.name == "mappedMethod" },
            "unmapped subclass override should rename through the " +
                "late-stamped component, got ${a.methods.map { it.name }}")
    }

    @Test
    fun `index composes with inputs winning collisions`() = runBlocking {
        val dir = createTempDirectory()
        // classpath jar carries an eqm; the input jar shadows it —
        // index(inputs) + index(classpath) ordering decides which
        // hierarchy the class resolves against
        val cp = dir.resolve("cp.jar")
        writeJar(cp, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(voidMethod("method_1"))
            },
        ))
        val input = dir.resolve("in.jar")
        writeJar(input, mapOf(
            "sub/C.class" to classBytes("sub/C") {
                superName = "eqm"
                methods.add(voidMethod("method_1"))
            },
        ))
        val batch = AsmRemapper.prepareBatch(
            listOf(tree()), obf, named,
            index = LazyASMInheritanceTree.index(setOf(input, cp))
        )
        val out = dir.resolve("out.jar")
        batch.use { it.remap(input, out, ASMInheritanceTree.scan(setOf(input))) }
        val c = readClasses(out).getValue("sub/C")
        assertTrue(c.methods.any { it.name == "mappedMethod" },
            "sub/C override should propagate to mappedMethod")
    }

    @Test
    fun `streaming path applies fixParamAnnotations`() = runBlocking {
        val dir = createTempDirectory()
        // the streaming path (all extensions perClass — the default
        // here) must run the same per-class JarFixups the fused path
        // does: an inner-class ctor whose phantom annotable-param
        // count includes the synthetic outer ref gets shifted down
        val input = dir.resolve("in.jar")
        writeJar(input, mapOf(
            "pkg/Outer\$Inner.class" to classBytes("pkg/Outer\$Inner") {
                innerClasses.add(
                    org.objectweb.asm.tree.InnerClassNode(
                        "pkg/Outer\$Inner", "pkg/Outer", "Inner", Opcodes.ACC_PUBLIC))
                methods.add(
                    MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Lpkg/Outer;I)V", null, null).apply {
                        visibleAnnotableParameterCount = 2
                        @Suppress("UNCHECKED_CAST")
                        visibleParameterAnnotations =
                            arrayOfNulls<List<org.objectweb.asm.tree.AnnotationNode>>(2).apply {
                                this[0] = listOf(org.objectweb.asm.tree.AnnotationNode("Lphantom;"))
                                this[1] = listOf(org.objectweb.asm.tree.AnnotationNode("Lreal;"))
                            } as Array<List<org.objectweb.asm.tree.AnnotationNode>>
                        visitCode()
                        visitVarInsn(Opcodes.ALOAD, 0)
                        visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                        visitInsn(Opcodes.RETURN)
                        visitMaxs(1, 3)
                        visitEnd()
                    })
            },
        ))
        val batch = AsmRemapper.prepareBatch(
            listOf(tree()), obf, named, libraries = emptySet())
        val out = dir.resolve("out.jar")
        batch.use { it.remap(input, out, ASMInheritanceTree.scan(setOf(input))) }
        val cls = readClasses(out).getValue("pkg/Outer\$Inner")
        val init = cls.methods.first { it.name == "<init>" }
        assertEquals(1, init.visibleAnnotableParameterCount,
            "phantom synthetic annotation should shift out in streaming remap")
        assertEquals("Lreal;", init.visibleParameterAnnotations[0][0].desc)
    }

}
