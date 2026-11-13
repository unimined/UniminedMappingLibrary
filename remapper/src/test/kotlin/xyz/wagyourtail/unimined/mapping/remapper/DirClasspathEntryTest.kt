package xyz.wagyourtail.unimined.mapping.remapper

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.LazyASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Exploded classpath entries (Gradle `sourceSet.output` deps — the
 * `build/classes` and `build/resources` dirs) must index and
 * materialize like jars; previously `ZipFile` blew up on them with
 * `FileNotFoundException: (Is a directory)`.
 */
class DirClasspathEntryTest {

    private fun classBytes(name: String, superName: String, vararg ifaces: String): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, ifaces)
        cw.visitMethod(Opcodes.ACC_PUBLIC, "thing", "()V", null, null).also {
            it.visitCode(); it.visitInsn(Opcodes.RETURN); it.visitMaxs(0, 1); it.visitEnd()
        }
        cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    @Test
    fun `index and materialize handle exploded classpath dirs`() {
        val dir = createTempDirectory("exploded")
        val pkg = dir.resolve("test").createDirectories()
        pkg.resolve("DirClass.class").writeBytes(
            classBytes("test/DirClass", "test/Parent", "test/Iface")
        )
        val ignored = dir.resolve("META-INF/versions/21").createDirectories()
        ignored.resolve("Hidden.class").writeBytes(classBytes("Hidden", "java/lang/Object"))

        val jar = dir.resolve("lib.jar")
        ZipOutputStream(jar.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("test/JarClass.class"))
            zos.write(classBytes("test/JarClass", "java/lang/Object"))
            zos.closeEntry()
        }

        val index = LazyASMInheritanceTree.index(setOf(dir, jar))
        // index maps each class to its classpath root — the dir itself
        assertEquals(dir, index[InternalName.read("test/DirClass")])
        assertEquals(jar, index[InternalName.read("test/JarClass")])
        // META-INF/versions classes must not index (same as zips)
        assertEquals(null, index[InternalName.read("Hidden")])

        val tree = LazyASMInheritanceTree(
            Namespace("src"),
            ASMInheritanceTree.ScanData(emptyMap()),
            index,
        )
        val dirInfo = tree.classes[InternalName.read("test/DirClass")]
        assertNotNull(dirInfo)
        assertEquals(
            InternalName.read("test/Parent"),
            dirInfo.superType
        )
        val jarInfo = tree.classes[InternalName.read("test/JarClass")]
        assertNotNull(jarInfo)
        tree.close()
    }

    @Test
    fun `eager scan handles exploded classpath dirs`() {
        val dir = createTempDirectory("exploded-scan")
        dir.resolve("pkg/EagerClass.class").also {
            it.parent.createDirectories()
            it.writeBytes(classBytes("pkg/EagerClass", "pkg/Base"))
        }
        val scanned = ASMInheritanceTree.scan(setOf(dir))
        val info = scanned.classes[InternalName.read("pkg/EagerClass")]
        assertNotNull(info)
        assertEquals(InternalName.read("pkg/Base"), info.superType)
    }
}
