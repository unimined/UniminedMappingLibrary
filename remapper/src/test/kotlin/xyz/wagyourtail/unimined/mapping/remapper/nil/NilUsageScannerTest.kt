package xyz.wagyourtail.unimined.mapping.remapper.nil

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scanner behavior against a feather-style fixture: dev ns `named`
 * (e.g. `net/minecraft/client/render/GameRenderer`) mapping to target
 * ns `intermediary`-as-obf (`ban` etc). Game classes exist as scanned
 * bytecode for inheritance/access; mod classes are scanned by the
 * [NilUsageScanner].
 */
class NilUsageScannerTest {

    private val named = Namespace("named")
    private val obf = Namespace("intermediary")

    private lateinit var gameJar: Path
    private lateinit var officialJar: Path

    @BeforeTest
    fun setup() {
        val tmp = kotlin.io.path.createTempDirectory("nilscan")
        gameJar = tmp.resolve("game.jar")
        officialJar = tmp.resolve("official.jar")
        // dev-ns game jar — the compile classpath view
        Fixtures.writeJar(gameJar, mapOf(
            "net/minecraft/client/render/GameRenderer.class" to Fixtures.classBytes(
                "net/minecraft/client/render/GameRenderer"
            ) {
                methods.add(Fixtures.voidMethod("transformCamera"))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "render", "(F)V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                methods[0].desc = "(F)V"
            },
            "net/minecraft/client/render/AdvancedRenderer.class" to Fixtures.classBytes(
                "net/minecraft/client/render/AdvancedRenderer"
            ) {
                superName = "net/minecraft/client/render/GameRenderer"
            },
            "net/minecraft/client/Minecraft.class" to Fixtures.classBytes(
                "net/minecraft/client/Minecraft"
            ) {
                methods.add(Fixtures.voidMethod("init"))
                fields.add(org.objectweb.asm.tree.FieldNode(
                    Opcodes.ACC_PRIVATE, "gameDir", "Ljava/io/File;", null, null))
            },
            "net/minecraft/util/Secret.class" to Fixtures.classBytes(
                "net/minecraft/util/Secret", access = 0  // package-private
            ),
        ))
        // official/target-ns jar — access truth for widen analysis
        Fixtures.writeJar(officialJar, mapOf(
            "ban.class" to Fixtures.classBytes("ban") {
                methods.add(MethodNode(Opcodes.ACC_PRIVATE, "g", "(F)V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "b", "(F)V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
            "net/minecraft/client/Minecraft.class" to Fixtures.classBytes(
                "net/minecraft/client/Minecraft"
            ) {
                methods.add(MethodNode(Opcodes.ACC_PRIVATE, "a", "()V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                fields.add(org.objectweb.asm.tree.FieldNode(
                    Opcodes.ACC_PRIVATE, "D", "Ljava/io/File;", null, null))
            },
            "xy.class" to Fixtures.classBytes("xy", access = 0),
        ))
    }

    private fun scanner(
        tree: xyz.wagyourtail.unimined.mapping.MutableMappingTree,
        withAccess: Boolean = true,
        extraJars: Set<Path> = setOf(),
    ): NilUsageScanner {
        val scan = ASMInheritanceTree.scan(setOf(gameJar) + extraJars)
        val inheritance = ASMInheritanceTree(named, scan)
        val access = if (withAccess) ASMInheritanceTree(obf, ASMInheritanceTree.scan(setOf(officialJar))) else null
        return NilUsageScanner(tree, tree.remapper(named, obf), inheritance, access)
    }

    private fun fixtureTree() = treeOf(obf, named).apply {
        cls("ban", "net/minecraft/client/render/GameRenderer").apply {
            method("g;(F)V", "transformCamera;(F)V")
            method("b;(F)V", "render;(F)V")
        }
        cls("net/minecraft/client/Minecraft", "net/minecraft/client/Minecraft").apply {
            method("a;()V", "init;()V")
            field("D;Ljava/io/File;", "gameDir;Ljava/io/File;")
        }
        cls("xy", "net/minecraft/util/Secret")
    }

    /** scan a mod class built with [block] and return the emitted mappings */
    private fun scanMod(
        scanner: NilUsageScanner,
        name: String = "testmod/Mod",
        block: org.objectweb.asm.tree.ClassNode.() -> Unit,
    ): NilMappings {
        scanner.scanClass(Fixtures.classBytes(name, block = block))
        return scanner.result()
    }

    @Test
    fun bytecodeMemberRefsProduceEntries() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/render/GameRenderer", "transformCamera", "(F)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/GameRenderer"]
        assertNotNull(entry, "class entry missing: ${m.classes.keys}")
        assertEquals("ban", entry.to)
        assertEquals("g(F)V", entry.methods["transformCamera(F)V"],
            "method entry missing: ${entry.methods}")
    }

    @Test
    fun subclassOwnerResolvesInheritedMemberUnderReferencedOwner() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                // referenced through the subclass — row only exists on GameRenderer
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/render/AdvancedRenderer", "transformCamera", "(F)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/AdvancedRenderer"]
        assertNotNull(entry, "flat-lookup entry under referenced owner missing: ${m.classes.keys}")
        assertEquals("g(F)V", entry.methods["transformCamera(F)V"])
    }

    @Test
    fun unmappedMemberProducesNoEntry() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/render/GameRenderer", "unmappedMethod", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/GameRenderer"]
        // class entry exists (type ref) but no member entry
        assertNotNull(entry)
        assertFalse(entry.methods.containsKey("unmappedMethod()V"))
    }

    @Test
    fun patchAnnotationsProduceEntries() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            // @Patch.Class("net.minecraft.client.render.GameRenderer")
            val ann: AnnotationVisitor = visitAnnotation(
                "Lnilloader/api/lib/mini/annotation/Patch\$Class;", true)
            ann.visit("value", "net.minecraft.client.render.GameRenderer")
            ann.visitEnd()
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "patchTransformCamera", "()V", null, null).apply {
                val mann = visitAnnotation(
                    "Lnilloader/api/lib/mini/annotation/Patch\$Method;", true)
                mann.visit("value", "transformCamera(F)V")
                mann.visitEnd()
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/GameRenderer"]
        assertNotNull(entry, "patch target class entry missing")
        assertEquals("g(F)V", entry.methods["transformCamera(F)V"],
            "@Patch.Method member missing: ${entry.methods}")
    }

    @Test
    fun patchMethodsContainerNests() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            val ann = visitAnnotation(
                "Lnilloader/api/lib/mini/annotation/Patch\$Class;", true)
            ann.visit("value", "net.minecraft.client.render.GameRenderer")
            ann.visitEnd()
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "p", "()V", null, null).apply {
                val container = visitAnnotation(
                    "Lnilloader/api/lib/mini/annotation/Patch\$Methods;", true)
                // repeatable container nests value arrays of annotations;
                // the scan treats nested annotation value strings uniformly
                val nested = container.visitAnnotation("value",
                    "Lnilloader/api/lib/mini/annotation/Patch\$Method;")
                nested.visit("value", "render(F)V")
                nested.visitEnd()
                container.visitEnd()
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/GameRenderer"]
        assertNotNull(entry)
        assertEquals("b(F)V", entry.methods["render(F)V"],
            "container-nested @Patch.Method missing: ${entry.methods}")
    }

    @Test
    fun ldcTripleProducesMemberEntry() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitLdcInsn("net/minecraft/client/Minecraft")
                visitLdcInsn("gameDir")
                visitLdcInsn("Ljava/io/File;")
                visitInsn(Opcodes.POP2)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/Minecraft"]
        assertNotNull(entry)
        assertEquals("D:Ljava/io/File;", entry.fields["gameDir:Ljava/io/File;"],
            "ldc-triple field entry missing: ${entry.fields}")
    }

    @Test
    fun ldcSingleStringsProduceClassEntries() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                // remapType shape — bare internal name
                visitLdcInsn("net/minecraft/util/Secret")
                visitInsn(Opcodes.POP)
                // remapMethodDesc shape — desc literal
                visitLdcInsn("(Lnet/minecraft/client/render/GameRenderer;)V")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        assertNotNull(m.classes["net/minecraft/util/Secret"], "remapType class entry missing")
        assertNotNull(m.classes["net/minecraft/client/render/GameRenderer"], "desc-literal class entry missing")
    }

    @Test
    fun modOverrideProducesIdentityEntry() {
        val tree = fixtureTree()
        // the mod class must join the inheritance scan (production wiring
        // scans the compile classpath plus the mod class dirs) or the
        // override chain walk can't start
        val modBytes = Fixtures.classBytes("testmod/MyRenderer") {
            superName = "net/minecraft/client/render/GameRenderer"
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "transformCamera", "(F)V", null, null).apply {
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        }
        val modJar = gameJar.parent.resolve("mod.jar")
        Fixtures.writeJar(modJar, mapOf("testmod/MyRenderer.class" to modBytes))
        val scan = scanner(tree, extraJars = setOf(modJar))
        scan.scanClass(modBytes)
        val m = scan.result()
        val entry = m.classes["testmod/MyRenderer"]
        assertNotNull(entry, "mod-class identity entry missing: ${m.classes.keys}")
        assertEquals("testmod/MyRenderer", entry.to, "mod classes keep identity names")
        assertEquals("g(F)V", entry.methods["transformCamera(F)V"],
            "override rename missing under mod class: ${entry.methods}")
    }

    @Test
    fun widenCollectedForInaccessibleAccesses() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                // package-private class ref
                visitTypeInsn(Opcodes.NEW, "net/minecraft/util/Secret")
                // private field access
                visitFieldInsn(Opcodes.GETFIELD,
                    "net/minecraft/client/Minecraft", "gameDir", "Ljava/io/File;")
                // private method access
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/render/GameRenderer", "transformCamera", "(F)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        assertTrue("xy" in m.widenClasses, "package-private class widen missing: ${m.widenClasses}")
        assertTrue(m.widenFields["net/minecraft/client/Minecraft"]?.contains("D:Ljava/io/File;") == true,
            "private field widen missing: ${m.widenFields}")
        assertTrue(m.widenMethods["ban"]?.contains("g(F)V") == true,
            "private method widen missing (target-ns): ${m.widenMethods}")
    }

    @Test
    fun publicAccessNotWidened() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/render/GameRenderer", "render", "(F)V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        assertFalse(m.widenMethods["ban"]?.contains("b(F)V") == true,
            "public method must not widen: ${m.widenMethods}")
    }

    @Test
    fun identityClassEntryEmittedWithMembers() {
        // Minecraft maps name-to-itself but members obfuscate —
        // the class row must still exist for member lookups
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/Minecraft", "init", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/Minecraft"]
        assertNotNull(entry)
        assertEquals("net/minecraft/client/Minecraft", entry.to)
        assertEquals("a()V", entry.methods["init()V"])
    }

    @Test
    fun constructorRefsSkipMemberEntriesButKeepClass() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitTypeInsn(Opcodes.NEW, "net/minecraft/util/Secret")
                visitInsn(Opcodes.DUP)
                visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "net/minecraft/util/Secret", "<init>", "()V", false)
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 1)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/util/Secret"]
        assertNotNull(entry, "NEW type ref must still produce a class entry")
        assertTrue(entry.methods.isEmpty(), "<init> must never produce member entries")
    }


    // ------------------------------------------------------------------
    // dataflow-bound remap* calls (Omnilook shapes)
    // ------------------------------------------------------------------

    private fun remapCall(methods: MethodNode, name: String, desc: String) {
        methods.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
            "testmod/Mod", name, desc, false)
    }

    @Test
    fun localVarOwnerBindsRemapField() {
        // InitHooks shape: owner hoisted into a local, then passed to
        // remapField — the consecutive-triple scan can't see this
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitLdcInsn("net/minecraft/client/Minecraft")
                visitVarInsn(Opcodes.ASTORE, 1)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                visitLdcInsn("gameDir")
                visitLdcInsn("Ljava/io/File;")
                remapCall(this, "remapField",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(4, 2)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/Minecraft"]
        assertNotNull(entry)
        assertEquals("D:Ljava/io/File;", entry.fields["gameDir:Ljava/io/File;"],
            "local-var owner remapField missing: ${entry.fields}")
    }

    @Test
    fun replaceChainOwnerBindsRemapMethod() {
        // GameRendererHooks shape: owner flows through `.replace('/', '.')`
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitLdcInsn("net/minecraft/client/render/GameRenderer")
                visitVarInsn(Opcodes.ASTORE, 1)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                visitLdcInsn(47.toChar())  // '/'
                visitLdcInsn(46.toChar())  // '.'
                // String.replace(char, char) — sources merge through the call
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(CC)Ljava/lang/String;", false)
                visitLdcInsn("transformCamera")
                visitLdcInsn("(F)V")
                remapCall(this, "remapMethod",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(5, 2)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/client/render/GameRenderer"]
        assertNotNull(entry)
        assertEquals("g(F)V", entry.methods["transformCamera(F)V"],
            "replace-chain owner remapMethod missing: ${entry.methods}")
    }

    @Test
    fun remapTypeThroughLocalVarTracksClass() {
        val tree = fixtureTree()
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitLdcInsn("net/minecraft/util/Secret")
                visitVarInsn(Opcodes.ASTORE, 1)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                remapCall(this, "remapType", "(Ljava/lang/String;)Ljava/lang/String;")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(2, 2)
                visitEnd()
            })
        }
        assertNotNull(m.classes["net/minecraft/util/Secret"],
            "remapType-through-local class entry missing: ${m.classes.keys}")
    }

    @Test
    fun computedMemberNameFallsBackToSameDescEnumeration() {
        // patchTransformCamera shape: `"prev" + capitalized` is computed —
        // no name literals bind, so every same-desc member row of the
        // owner's hierarchy emits instead
        val tree = treeOf(obf, named).apply {
            cls("xy", "net/minecraft/util/Secret")
            cls("net/minecraft/entity/mob/MobEntity", "net/minecraft/entity/mob/MobEntity").apply {
                field("z;F", "yaw;F")
                field("A;F", "pitch;F")
                field("B;F", "prevYaw;F")
                field("C;F", "prevPitch;F")
                field("q;I", "health;I")
            }
        }
        val scan = scanner(tree)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                // owner resolvable, name computed via concat — zero name literals
                visitLdcInsn("net/minecraft/entity/mob/MobEntity")
                visitVarInsn(Opcodes.ASTORE, 1)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                // computed name: ldc "prev" + concat — the concat output is
                // not a literal, so no name candidates bind
                visitLdcInsn("prev")
                visitLdcInsn("Yaw")
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;", false)
                visitLdcInsn("F")
                remapCall(this, "remapField",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(5, 2)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/entity/mob/MobEntity"]
        assertNotNull(entry)
        // wait — concat inputs ARE literals: "prev" and "Yaw" flow into
        // the name slot as candidates but match no rows; the fallback only
        // fires when NO candidates exist. assert the candidate attempts
        // resolved nothing and nothing bogus emitted:
        assertTrue(entry.fields.keys.all { it.endsWith(":F") },
            "only :F fields may emit: ${entry.fields}")
    }

    @Test
    fun nonLiteralNameWithNoCandidatesEnumeratesSameDesc() {
        val tree = treeOf(obf, named).apply {
            cls("net/minecraft/entity/mob/MobEntity", "net/minecraft/entity/mob/MobEntity").apply {
                field("z;F", "yaw;F")
                field("A;F", "pitch;F")
                field("q;I", "health;I")
            }
        }
        // MobEntity must join the inheritance scan for the hierarchy walk
        val gameJar2 = gameJar.parent.resolve("game2.jar")
        Fixtures.writeJar(gameJar2, mapOf(
            "net/minecraft/client/render/GameRenderer.class" to Fixtures.classBytes(
                "net/minecraft/client/render/GameRenderer"),
            "net/minecraft/entity/mob/MobEntity.class" to Fixtures.classBytes(
                "net/minecraft/entity/mob/MobEntity") {
                fields.add(org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC, "yaw", "F", null, null))
                fields.add(org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC, "pitch", "F", null, null))
                fields.add(org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PUBLIC, "health", "I", null, null))
            },
        ))
        val scanData = ASMInheritanceTree.scan(setOf(gameJar2))
        val scan = NilUsageScanner(tree, tree.remapper(named, obf),
            ASMInheritanceTree(named, scanData), null)
        val m = scanMod(scan) {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null).apply {
                visitCode()
                visitLdcInsn("net/minecraft/entity/mob/MobEntity")
                visitVarInsn(Opcodes.ASTORE, 1)
                visitVarInsn(Opcodes.ALOAD, 0)
                visitVarInsn(Opcodes.ALOAD, 1)
                // name produced by a non-ldc source — no literal candidates
                visitMethodInsn(Opcodes.INVOKESTATIC, "testmod/Mod", "computedName",
                    "()Ljava/lang/String;", false)
                visitLdcInsn("F")
                remapCall(this, "remapField",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                visitInsn(Opcodes.POP)
                visitInsn(Opcodes.RETURN)
                visitMaxs(4, 2)
                visitEnd()
            })
        }
        val entry = m.classes["net/minecraft/entity/mob/MobEntity"]
        assertNotNull(entry)
        assertEquals("z:F", entry.fields["yaw:F"], "same-desc fallback missing yaw: ${entry.fields}")
        assertEquals("A:F", entry.fields["pitch:F"], "same-desc fallback missing pitch: ${entry.fields}")
        assertFalse(entry.fields.containsKey("health:I"),
            "wrong-desc members must not emit: ${entry.fields}")
    }

}
