package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.coroutines.test.runTest
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.names.ProGuardMapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full-pipeline e2e: compile a "base" project with javac, generate ProGuard-style
 * obf mappings for it, obfuscate the base jar, then compile a "mod" against the
 * named base and remap it back to obf — exercising shared overload names,
 * inheritance-driven member propagation, an access-widener overlay, and
 * META-INF/services fixups end to end.
 */
class ObfuscationE2ETest {

    private val baseSources = mapOf(
        "base/Abstract.java" to """
            package base;
            public abstract class Abstract {
                private void secret() { }
                public void over() { }
            }
        """.trimIndent(),
        "base/Service.java" to """
            package base;
            public interface Service { String name(); }
        """.trimIndent(),
        "base/Impl.java" to """
            package base;
            public class Impl implements Service { public String name() { return "impl"; } }
        """.trimIndent(),
        "base/Overloads.java" to """
            package base;
            public class Overloads {
                public void alpha() { }
                public int beta(int x) { return x; }
                public String gamma(String s) { return s; }
            }
        """.trimIndent(),
    )

    private val modSources = mapOf(
        "mod/Mod.java" to """
            package mod;
            public class Mod extends base.Abstract {
                @Override public void over() { }
                public void secret() { } // same shape as base's private method — becomes an override via AT
                public void extra() { }
            }
        """.trimIndent(),
        "mod/ModService.java" to """
            package mod;
            public class ModService implements base.Service { public String name() { return "mod"; } }
        """.trimIndent(),
    )

    private fun MutableMappingTree.obfMethod(cls: String, name: String, desc: String): String =
        getClass(named, InternalName.unchecked(cls))!!
            .getMethod(named, UnqualifiedName.unchecked(name), MethodDescriptor.unchecked(desc))!!
            .names[obf]!!.value

    private fun MutableMappingTree.obfClass(cls: String): String =
        getClass(named, InternalName.unchecked(cls))!!.names[obf]!!.value

    @Test
    fun obfuscateBaseThenRemapModWithOverlay() = runTest {
        val dir = createTempDirectory()

        // --- build the base project (named space) ---
        val baseJar = FixtureProject.compile(
            dir.resolve("base"), baseSources,
            resources = mapOf("META-INF/services/base.Service" to "base.Impl".toByteArray()),
        )

        // --- generate proguard-style obf mappings over the base jar ---
        val tree = MutableMappingTree(setOf(obf, named))
        ProGuardMapper(tree, named, obf).apply(setOf(baseJar))

        // --- the "mod"'s overlay layer: AT widening secret() private -> protected ---
        val atTree = treeOf()
        val atCls = atTree.cls(tree.obfClass("base/Abstract"), "base/Abstract")
        val atMethod = atCls.method(mapOf(
            named to MethodNameAndDescriptor.unchecked("secret;()V"),
            obf to MethodNameAndDescriptor.unchecked("${tree.obfMethod("base/Abstract", "secret", "()V")};()V"),
        )) as MutableMethodMapping
        atMethod.addMetadata(AccessElement(AddRemove.REMOVE, AccessFlag.PRIVATE, AccessConditions.ALL))
        atMethod.addMetadata(AccessElement(AddRemove.ADD, AccessFlag.PROTECTED, AccessConditions.ALL))

        // --- obfuscate the base jar (overlay merged, no inheritance needed) ---
        val baseObf = dir.resolve("base-obf.jar")
        AsmRemapper.remap(baseJar, baseObf, AsmRemapper.overlay(listOf(tree, atTree)), named, obf)

        val obfEntries = readEntries(baseObf)
        val shortClass = Regex("[a-z]+\\.class")
        val obfClasses = obfEntries.filter { it.endsWith(".class") }
        assertTrue(obfClasses.isNotEmpty() && obfClasses.all { shortClass.matches(it.substringAfterLast('/')) },
            "all class entries should be short obf names: $obfClasses")

        val obfBase = readClasses(baseObf)

        // overload sharing: Overloads' three methods all became 'a' (descs differ)
        val obfOverloads = obfBase.getValue(tree.obfClass("base/Overloads"))
        val names = obfOverloads.methods.filter { !it.name.startsWith("<") }.map { it.name }
        assertEquals(listOf("a", "a", "a"), names.sorted(), "shared name across differing descs")

        // AT applied at bytecode level: secret is protected in the obf jar
        val obfSecret = obfBase.getValue(tree.obfClass("base/Abstract"))
            .methods.first { it.name == tree.obfMethod("base/Abstract", "secret", "()V") }
        assertTrue(obfSecret.access and Opcodes.ACC_PROTECTED != 0 && obfSecret.access and Opcodes.ACC_PRIVATE == 0)

        // service file renamed + impl content remapped
        val svcEntry = "META-INF/services/${tree.obfClass("base/Service")}"
        assertTrue(svcEntry in obfEntries, "service file should be renamed to obf name: $obfEntries")
        ZipFile(baseObf.toFile()).use { z ->
            assertEquals(tree.obfClass("base/Impl").replace('/', '.'),
                z.getInputStream(z.getEntry(svcEntry)).readBytes().toString(Charsets.UTF_8))
        }

        // --- compile the mod against the named base jar ---
        val modJar = FixtureProject.compile(
            dir.resolve("mod"), modSources,
            resources = mapOf("META-INF/services/base.Service" to "mod.ModService".toByteArray()),
            classpath = listOf(baseJar),
        )

        // --- remap mod named -> obf: base mappings + AT overlay + inheritance over both jars ---
        val modObf = dir.resolve("mod-obf.jar")
        AsmRemapper.remap(modJar, modObf, listOf(tree, atTree), named, obf, libraries = setOf(baseJar))

        val modOut = readClasses(modObf).getValue("mod/Mod")
        assertEquals(tree.obfClass("base/Abstract"), modOut.superName)
        val modMethods = modOut.methods.filter { !it.name.startsWith("<") }.map { it.name }
        assertTrue(tree.obfMethod("base/Abstract", "over", "()V") in modMethods,
            "real override should get the obf name: $modMethods")
        assertTrue(tree.obfMethod("base/Abstract", "secret", "()V") in modMethods,
            "AT-widened override should propagate the obf name: $modMethods")
        assertTrue("extra" in modMethods, "unmapped member should pass through: $modMethods")

        // service file in the mod jar renamed, unmapped impl content passes through
        val modEntries = readEntries(modObf)
        assertTrue(svcEntry in modEntries, "mod service file should be renamed: $modEntries")
        ZipFile(modObf.toFile()).use { z ->
            assertEquals("mod.ModService", z.getInputStream(z.getEntry(svcEntry)).readBytes().toString(Charsets.UTF_8))
        }
    }

}
