package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Member *references* scoped through a subclass must resolve to the
 * declaring ancestor's row — the `getfield Child/c` where `c` is
 * declared on `Parent` shape (the forge-fg2 `RegistryNamespaced.c ->
 * RegistrySimple.field_148758_b` launch regression). The declaration
 * renames while the reference keeps the source name → NoSuchFieldError.
 */
class SubclassScopedMemberRefTest {

    /** pa{int c}, cb extends pa; cb.<init> runs `getfield cb/c I` */
    private fun subclassFieldRefJar() = mapOf(
        "pa.class" to classBytes("pa") {
            fields.add(FieldNode(Opcodes.ACC_PUBLIC, "c", "I", null, null))
        },
        "cb.class" to classBytes("cb") {
            superName = "pa"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitFieldInsn(Opcodes.GETFIELD, "cb", "c", "I")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(2, 1)
                visitEnd()
            })
        },
    )

    private fun subclassMethodRefJar() = mapOf(
        "pa.class" to classBytes("pa") {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "foo", "()V", null, null).apply {
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        },
        "cb.class" to classBytes("cb") {
            superName = "pa"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cb", "foo", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(1, 1)
                visitEnd()
            })
        },
    )

    @Test
    fun fieldRefThroughSubclassResolves() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val t = treeOf()
        t.cls("pa", "pkg/Parent").field("c;I", "parentField;I")
        t.cls("cb", "pkg/Child")

        writeJar(mc, subclassFieldRefJar())
        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/Child")
        val getfield = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<FieldInsnNode>().single()
        assertEquals("parentField", getfield.name,
            "subclass-scoped field ref must take the declaring parent's mapped name")
    }

    @Test
    fun methodRefThroughSubclassResolves() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val t = treeOf()
        t.cls("pa", "pkg/Parent").method("foo;()V", "parentMethod;()V")
        t.cls("cb", "pkg/Child")

        writeJar(mc, subclassMethodRefJar())
        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/Child")
        val call = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<MethodInsnNode>().single { it.name != "<init>" }
        assertEquals("parentMethod", call.name,
            "subclass-scoped method ref must take the declaring parent's mapped name")
    }

    /**
     * Three-column tree where the *target* name also appears in a
     * sibling target column — srg `field_x` == named `field_x` (mcp
     * leaves unnamed members on searge / fillMissingNames hop-fills).
     * The srg cell is a genuine rename for the obf->srg route, not an
     * echo — the remap must still apply it.
     */
    @Test
    fun fieldRefThroughSubclassWithCollidingTargetColumns() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val srg = Namespace("srg")
        val t = treeOf(obf, srg, named)
        val pa = t.clazz(mapOf(
            obf to InternalName.unchecked("pa"),
            srg to InternalName.unchecked("pkg/Parent"),
            named to InternalName.unchecked("pkg/Parent"),
        ))
        pa.field(mapOf(
            obf to FieldNameAndDescriptor.unchecked("c;I"),
            srg to FieldNameAndDescriptor.unchecked("field_x;I"),
            named to FieldNameAndDescriptor.unchecked("field_x;I"),
        ))
        t.clazz(mapOf(
            obf to InternalName.unchecked("cb"),
            srg to InternalName.unchecked("pkg/Child"),
            named to InternalName.unchecked("pkg/Child"),
        ))

        writeJar(mc, subclassFieldRefJar())
        AsmRemapper.remap(mc, out, listOf(t), obf, srg)

        val c = readClasses(out).getValue("pkg/Child")
        val getfield = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<FieldInsnNode>().single()
        assertEquals("field_x", getfield.name,
            "a target cell equal to another target column is a genuine rename, not an echo")
    }

    /**
     * Same colliding-column shape for methods — group candidates must
     * not treat a target cell echoing another *target* column as a
     * non-rename (the yarn `method_28484` survivor shape).
     */
    @Test
    fun methodRefThroughSubclassWithCollidingTargetColumns() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val srg = Namespace("srg")
        val t = treeOf(obf, srg, named)
        val pa = t.clazz(mapOf(
            obf to InternalName.unchecked("pa"),
            srg to InternalName.unchecked("pkg/Parent"),
            named to InternalName.unchecked("pkg/Parent"),
        ))
        pa.method(mapOf(
            obf to xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor.unchecked("foo;()V"),
            srg to xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor.unchecked("func_x;()V"),
            named to xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor.unchecked("func_x;()V"),
        ))
        t.clazz(mapOf(
            obf to InternalName.unchecked("cb"),
            srg to InternalName.unchecked("pkg/Child"),
            named to InternalName.unchecked("pkg/Child"),
        ))

        writeJar(mc, subclassMethodRefJar())
        AsmRemapper.remap(mc, out, listOf(t), obf, srg)

        val c = readClasses(out).getValue("pkg/Child")
        val call = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<MethodInsnNode>().single { it.name != "<init>" }
        assertEquals("func_x", call.name,
            "a method target cell equal to another target column is a genuine rename, not an echo")
    }

    /**
     * The FML shadow shape: the child declares an *unmapped* field whose
     * name collides with the parent's mapped target name — FG2-era
     * `EntityPlayer.eyeHeight` (FML-added) vs `Entity.field_70129_M ->
     * eyeHeight` (yarn). A `Child/field_70129_M` ref must emit the
     * DECLARING owner (`pkg/Entity`) — under the child owner it binds
     * statically to the child's field, so `Entity.eyeHeight` stays 0 and
     * the player gets kicked `illegal stance: 0.0` at world join. The
     * child's own field ref keeps the child owner.
     */
    @Test
    fun fieldRefOwnerNormalizesToDeclaringClass() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val t = treeOf()
        t.cls("pa", "pkg/Entity").field("field_70129_M;F", "eyeHeight;F")
        t.cls("cb", "pkg/EntityPlayer")

        writeJar(mc, mapOf(
            "pa.class" to classBytes("pa") {
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "field_70129_M", "F", null, null))
            },
            "cb.class" to classBytes("cb") {
                superName = "pa"
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "eyeHeight", "F", null, null))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "pa", "<init>", "()V", false)
                    // `this.field_70129_M = 1.62f` — the inherited field
                    // ref'd through the child owner (javac shape)
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitLdcInsn(1.62f)
                    visitFieldInsn(Opcodes.PUTFIELD, "cb", "field_70129_M", "F")
                    // `this.eyeHeight = 1.62f` — the child's own field
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitLdcInsn(1.62f)
                    visitFieldInsn(Opcodes.PUTFIELD, "cb", "eyeHeight", "F")
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))
        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/EntityPlayer")
        val puts = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<FieldInsnNode>()
        assertEquals(2, puts.size)
        assertEquals("pkg/Entity", puts[0].owner,
            "inherited field ref must pin the declaring owner")
        assertEquals("eyeHeight", puts[0].name)
        assertEquals("pkg/EntityPlayer", puts[1].owner,
            "the child's own (unmapped) field ref keeps the child owner")
        assertEquals("eyeHeight", puts[1].name)
    }

    /**
     * The collision-free counterpart of [fieldRefOwnerNormalizesToDeclaringClass]:
     * `TerrainParticle` puts inherited `SingleQuadParticle.bCol` through its
     * own owner (javac shape), and no intermediate class declares a field
     * colliding with the target name. The ref must keep the *callsite*
     * owner — pinning here breaks exact-match tooling (mixin slice/injection
     * targets) that production remappers never rewrite: fabric-particles'
     * `TerrainParticleMixin` slices on `TerrainParticle;bCol`, and a pinned
     * `SingleQuadParticle.bCol` putfield never matches it.
     */
    @Test
    fun fieldRefOwnerUnpinnedWithoutCollision() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val t = treeOf()
        t.cls("pa", "pkg/SingleQuadParticle").field("field_62635;F", "bCol;F")
        t.cls("cb", "pkg/TerrainParticle")

        writeJar(mc, mapOf(
            "pa.class" to classBytes("pa") {
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "field_62635", "F", null, null))
            },
            "cb.class" to classBytes("cb") {
                superName = "pa"
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "pa", "<init>", "()V", false)
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitLdcInsn(0.6f)
                    visitFieldInsn(Opcodes.PUTFIELD, "cb", "field_62635", "F")
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))
        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/TerrainParticle")
        val puts = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<FieldInsnNode>()
        assertEquals(1, puts.size)
        assertEquals("pkg/TerrainParticle", puts[0].owner,
            "collision-free inherited field ref must keep the callsite owner " +
            "(production remapper parity; pinning breaks mixin exact-match)")
        assertEquals("bCol", puts[0].name)
    }

    /**
     * Diagnostic repro over the real forge-fg2 inputs — the 1.12.2
     * `merged-v3` obf jar + the composed official->searge umf bundle.
     * `fh.<init> -> getfield fh/c` must land on `field_82596_a` (declared
     * on parent `fo`). Skips when the fixture cache isn't present.
     */
    @Test
    fun forgeFg2RealDataRepro() = runTest {
        val base = java.io.File(
            "/run/media/william/m2shared/Projects/unimined-org/unimined/" +
                    "testing/forge-fg2/.gradle/unimined"
        )
        val bundle = base.resolve("../../build/unimined/mappings/main/prod-jar.json")
        val jar = base.resolve(
            "native-cache/versions/1.12.2/" +
                    "merged-v3-0f275bc1547d01fa5f56ba34bdc87d981ee12daf" +
                    "-886945bfb2b978778c3a0288fd7fab09d315b25f-b0a1daff.jar"
        )
        if (!bundle.exists() || !jar.exists()) return@runTest

        // the serialized bundle is a zip carrying views/uml-tree (UMFB)
        val payload = java.util.zip.ZipFile(bundle).use { z ->
            z.getInputStream(z.getEntry("views/uml-tree")).readBytes()
        }
        val tree = xyz.wagyourtail.unimined.mapping.MutableMappingTree.canonical()
        xyz.wagyourtail.unimined.mapping.formats.umf.UMFB.read(payload, tree)
        val official = Namespace("official")
        val srg = Namespace("searge")
        val mcp = Namespace("mcp")

        // tree-level sanity: fo must expose c -> field_82596_a
        run {
            val cls = tree.getClass(
                official,
                xyz.wagyourtail.unimined.mapping.jvms.four.two.one
                    .InternalName.unchecked("fo"),
            )
            println("fo class: ${cls?.names}")
            for (f in cls?.fields ?: emptyList())
                println("  field row names=${f.names} descs=${f.descs}")
            with(tree) {
                val hit = cls?.getField(
                    official,
                    xyz.wagyourtail.unimined.mapping.jvms.four.two.two
                        .UnqualifiedName.unchecked("c"),
                    xyz.wagyourtail.unimined.mapping.jvms.four.three.two
                        .FieldDescriptor.unchecked("Ljava/util/Map;"),
                )
                println("fo.c(Map) -> ${hit?.names}")
            }
        }

        // both the searge target and the actual fg2 production target
        // `mcp` — fo.c's mcp cell is a verbatim searge fill
        // (`mcp=field_82596_a`), so the mcp remap exercises the
        // echo-cell fallback path that shipped the launch regression
        for (target in listOf(srg, mcp)) {
            val out = java.nio.file.Files.createTempDirectory("fg2-repro")
                .resolve("out-${target.name}.jar")
            val batch = AsmRemapper.prepareBatch(
                listOf(tree), official, target,
                index = xyz.wagyourtail.unimined.mapping.inheritance
                    .LazyASMInheritanceTree.index(setOf(jar.toPath())),
            )
            batch.use {
                it.remap(
                    jar.toPath(), out,
                    xyz.wagyourtail.unimined.mapping.inheritance
                        .ASMInheritanceTree.scan(setOf(jar.toPath())),
                )
            }

            val node = java.util.zip.ZipFile(out.toFile()).use { zf ->
                val e = zf.entries().asSequence().first {
                    it.name == "net/minecraft/util/registry/" +
                            "RegistryNamespaced.class"
                }
                org.objectweb.asm.tree.ClassNode().also { cn ->
                    org.objectweb.asm.ClassReader(
                        zf.getInputStream(e).readBytes()
                    ).accept(cn, 0)
                }
            }
            val getfield = node.methods.single { it.name == "<init>" }
                .instructions.toArray()
                .filterIsInstance<org.objectweb.asm.tree.FieldInsnNode>()
                .single { it.name != "field_148759_a" && it.name != "field_148758_b" }
            println("[$target] getfield owner=${getfield.owner} name=${getfield.name}")
            assertEquals("field_82596_a", getfield.name,
                "real fg2 data ($target): subclass-scoped field ref must remap")
        }
    }

    /**
     * The production env-artifact shape: `prepareBatch` + `RemapBatch.remap`
     * with the input jar itself on the classpath index, a three-column
     * (official, srg, named) tree, and *name-only* field rows the way
     * tsrg/mcpconfig sources write them — the forge-fg2
     * `RegistryNamespaced.<init> -> getfield fh/c` regression shape.
     */
    @Test
    fun fieldRefThroughSubclassBatchPath() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val official = Namespace("official")
        val srg = Namespace("srg")
        val t = treeOf(official, srg, named)
        val fo = t.clazz(mapOf(
            official to InternalName.unchecked("fo"),
            srg to InternalName.unchecked("pkg/Parent"),
            named to InternalName.unchecked("pkg/Parent"),
        ))
        // name-only row — no descriptors, matching tsrg field rows
        fo.field(mapOf(
            official to FieldNameAndDescriptor.unchecked("c"),
            srg to FieldNameAndDescriptor.unchecked("field_x"),
            named to FieldNameAndDescriptor.unchecked("parentField"),
        ))
        val fh = t.clazz(mapOf(
            official to InternalName.unchecked("fh"),
            srg to InternalName.unchecked("pkg/Child"),
            named to InternalName.unchecked("pkg/Child"),
        ))
        fh.field(mapOf(
            official to FieldNameAndDescriptor.unchecked("b"),
            srg to FieldNameAndDescriptor.unchecked("field_y"),
            named to FieldNameAndDescriptor.unchecked("childField"),
        ))

        writeJar(mc, mapOf(
            "fo.class" to classBytes("fo") {
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "c", "Ljava/util/Map;", null, null))
            },
            "fh.class" to classBytes("fh") {
                superName = "fo"
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "a", "Lqz;", null, null))
                fields.add(FieldNode(Opcodes.ACC_PUBLIC, "b", "Ljava/util/Map;", null, null))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "fo", "<init>", "()V", false)
                    visitVarInsn(Opcodes.ALOAD, 0)
                    visitFieldInsn(Opcodes.GETFIELD, "fh", "c", "Ljava/util/Map;")
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))

        // the env-artifact remap path: the MC jar rides the classpath
        // index AND arrives as the input scan
        val batch = AsmRemapper.prepareBatch(
            listOf(t), official, srg,
            index = xyz.wagyourtail.unimined.mapping.inheritance
                .LazyASMInheritanceTree.index(setOf(mc)),
        )
        batch.use {
            it.remap(mc, out, xyz.wagyourtail.unimined.mapping.inheritance
                .ASMInheritanceTree.scan(setOf(mc)))
        }

        val c = readClasses(out).getValue("pkg/Child")
        val getfield = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<FieldInsnNode>().single()
        assertEquals("field_x", getfield.name,
            "batch remap: subclass-scoped field ref must take the declaring parent's srg name")
    }

    /**
     * The genuine echo case the sibling-column gate exists for: an
     * impl-side row's target cell merely copies the *source* name
     * (fillMissingNames identity `named=m`) — it must still lose to
     * the declaring member's real rename.
     */
    @Test
    fun echoOfSourceNameStillYields() = runTest {
        val dir = createTempDirectory()
        val mc = dir.resolve("mc.jar"); val out = dir.resolve("out.jar")

        val t = treeOf()
        t.cls("pa", "pkg/Parent").method("foo;()V", "parentMethod;()V")
        // impl class declares the same member but its target cell is a
        // verbatim copy of the source name — an echo, not a rename
        val cb = t.cls("cb", "pkg/Child")
        cb.method("foo;()V", "foo;()V")

        writeJar(mc, subclassMethodRefJar())
        AsmRemapper.remap(mc, out, listOf(t), obf, named)

        val c = readClasses(out).getValue("pkg/Child")
        val call = c.methods.single { it.name == "<init>" }.instructions.toArray()
            .filterIsInstance<MethodInsnNode>().single { it.name != "<init>" }
        assertEquals("parentMethod", call.name,
            "a target cell echoing the source name must yield to the component's real rename")
    }
}
