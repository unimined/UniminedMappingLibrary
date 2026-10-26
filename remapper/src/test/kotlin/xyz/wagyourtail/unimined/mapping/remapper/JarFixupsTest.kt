package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.asm.PackageAccess
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jar-level edge cases: multi-release entries land under their version dir,
 * package-private access broken by the rename gets detected/fixed.
 */
class JarFixupsTest {

    @Test
    fun multiReleaseEntriesRemapInLockstep() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm"),
            "META-INF/versions/9/eqm.class" to classBytes("eqm"),
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val entries = readEntries(output)
        assertTrue("net/minecraft/Example.class" in entries)
        assertTrue("META-INF/versions/9/net/minecraft/Example.class" in entries, "versioned entry should keep its version dir: $entries")
    }

    @Test
    fun fixPackageAccessUpgradesBrokenReferences() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        // a/Lib and b/User land in different packages post-rename
        tree.cls("a/Lib", "x/Lib")
        tree.cls("b/User", "y/User")

        writeJar(input, mapOf(
            // package-private class (no ACC_PUBLIC)
            "a/Lib.class" to classBytes("a/Lib", Opcodes.ACC_SUPER),
            // public class in another package that instantiates it
            "b/User.class" to classBytes("b/User") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "use", "()V", null, null).apply {
                    visitCode()
                    visitTypeInsn(Opcodes.NEW, "a/Lib")
                    visitInsn(Opcodes.DUP)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "a/Lib", "<init>", "()V", false)
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))

        // check-only: warns but leaves access alone
        val checked = dir.resolve("checked.jar")
        AsmRemapper.remap(input, checked, tree, obf, named, Options(packageAccess = PackageAccess.WARN))
        assertTrue(readClasses(checked).getValue("x/Lib").access and Opcodes.ACC_PUBLIC == 0)

        // fix: target upgraded to public
        AsmRemapper.remap(input, output, tree, obf, named, Options(packageAccess = PackageAccess.FIX))
        assertTrue(readClasses(output).getValue("x/Lib").access and Opcodes.ACC_PUBLIC != 0)
    }

    @Test
    fun packageAccessWithinSameMappedPackageIsFine() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val output = dir.resolve("out.jar")

        val tree = treeOf()
        // both land in newpkg/ — same package, no breakage
        tree.cls("a/Lib", "newpkg/Lib")
        tree.cls("b/User", "newpkg/User")

        writeJar(input, mapOf(
            "a/Lib.class" to classBytes("a/Lib", Opcodes.ACC_SUPER),
            "b/User.class" to classBytes("b/User") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "use", "()V", null, null).apply {
                    visitCode()
                    visitTypeInsn(Opcodes.NEW, "a/Lib")
                    visitInsn(Opcodes.DUP)
                    visitMethodInsn(Opcodes.INVOKESPECIAL, "a/Lib", "<init>", "()V", false)
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named, Options(packageAccess = PackageAccess.FIX))
        assertEquals(0, readClasses(output).getValue("newpkg/Lib").access and Opcodes.ACC_PUBLIC)
    }


    /** package-private *member* (not just class) crossing packages post-remap —
     *  a package-private field on a public class referenced from another package. */
    @Test
    fun packageAccessMemberLevel() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")
        val fixed = dir.resolve("fixed.jar")
        val checked = dir.resolve("checked.jar")

        val tree = treeOf()
        tree.cls("a/Lib", "x/Lib")   // public class, moves package
        tree.cls("b/User", "y/User")

        writeJar(input, mapOf(
            "a/Lib.class" to classBytes("a/Lib", Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER) {
                // package-private field — no PUBLIC/PRIVATE/PROTECTED
                fields.add(FieldNode(0, "counter", "I", null, null))
            },
            "b/User.class" to classBytes("b/User") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "use", "()V", null, null).apply {
                    visitCode()
                    visitInsn(Opcodes.ACONST_NULL)
                    visitFieldInsn(Opcodes.GETFIELD, "a/Lib", "counter", "I")
                    visitInsn(Opcodes.POP)
                    visitInsn(Opcodes.RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                })
            },
        ))

        // WARN leaves it alone
        AsmRemapper.remap(input, checked, tree, obf, named, Options(packageAccess = PackageAccess.WARN))
        assertEquals(0, readClasses(checked).getValue("x/Lib").fields.single().access and Opcodes.ACC_PUBLIC)

        // FIX upgrades the member to public
        AsmRemapper.remap(input, fixed, tree, obf, named, Options(packageAccess = PackageAccess.FIX))
        assertTrue(readClasses(fixed).getValue("x/Lib").fields.single().access and Opcodes.ACC_PUBLIC != 0)
    }


    /** .accesswidener + accesstransformer.cfg resources get their targets
     *  remapped like bytecode — otherwise the runtime AW/AT misses. */
    @Test
    fun accessFileResourcesRemap() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("a/Lib", "x/Lib").method("m_1;()V", "doThing;()V")

        writeJar(input, mapOf(
            "a/Lib.class" to classBytes("a/Lib"),
            "test.accesswidener" to """
                accessWidener v2 intermediary
                accessible class a/Lib
                accessible method a/Lib m_1 ()V
                accessible field a/Lib m_1 I
            """.trimIndent().toByteArray(),
            "META-INF/accesstransformer.cfg" to """
                public a.Lib # class comment
                protected a.Lib m_1()V
            """.trimIndent().toByteArray(),
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val entries = java.util.zip.ZipFile(output.toFile()).use { z ->
            z.entries().asSequence().associate { it.name to z.getInputStream(it).readBytes() }
        }
        val aw = String(entries.getValue("test.accesswidener"))
        assertTrue("accessWidener v2 named" in aw, "aw=\n" + aw)
        assertTrue("accessible class x/Lib" in aw)
        assertTrue("accessible method x/Lib doThing ()V" in aw)
        assertTrue("accessible field x/Lib m_1 I" in aw)  // no field row — name passthrough, owner remapped

        val at = String(entries.getValue("META-INF/accesstransformer.cfg"))
        assertTrue("public x/Lib" in at, "at=\n" + at)
        assertTrue("protected x/Lib doThing()V" in at)
    }

}
