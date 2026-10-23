package xyz.wagyourtail.unimined.mapping.test.remapper

import xyz.wagyourtail.unimined.mapping.remapper.source.SourceRemapper
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/** source+bytecode pairs through the PSI engine: declarations, member access,
 *  ctor calls and fully-qualified refs follow the mapping tree. */
class SourceRemapTest {

    /** compile .java sources → bytecode jar; returns (sourceJar, bytecodeJar) */
    private fun buildPair(dir: Path, sources: Map<String, String>): Pair<Path, Path> {
        val srcDir = dir.resolve("src"); val clsDir = dir.resolve("classes")
        val srcJar = dir.resolve("src.jar"); val binJar = dir.resolve("bin.jar")
        for ((name, text) in sources) {
            val p = srcDir.resolve(name)
            Files.createDirectories(p.parent)
            Files.write(p, text.toByteArray())
        }
        Files.createDirectories(clsDir)
        val javac = ToolProvider.getSystemJavaCompiler()
        val err = java.io.ByteArrayOutputStream()
        val args = listOf("-d", clsDir.toString(), "-proc:none") +
            sources.keys.map { srcDir.resolve(it).toString() }
        check(javac.run(null, null, err, *args.toTypedArray()) == 0) {
            "fixture javac failed: ${err}" }
        writeJar(srcJar, sources.mapValues { it.value.toByteArray() })
        writeJar(binJar, Files.walk(clsDir).filter { Files.isRegularFile(it) }
            .map { clsDir.relativize(it).toString().replace('\\', '/') to Files.readAllBytes(it) }
            .collect(java.util.stream.Collectors.toMap({ it.first }, { it.second })))
        return srcJar to binJar
    }

    @Test
    fun javaSourceRemap() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/Target.java" to """
                package a;
                public class Target {
                    public int f_1;
                    public void m_1() {}
                }
            """.trimIndent(),
            "b/Use.java" to """
                package b;
                public class Use {
                    public void call() {
                        a.Target t = new a.Target();
                        t.m_1();
                        int v = t.f_1;
                    }
                }
            """.trimIndent(),
        ))

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.field("f_1;I", "mappedField;I")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.entries().asSequence().filter { it.name.endsWith(".java") }
                .map { it.name to z.getInputStream(it).readBytes().decodeToString() }.toMap()
        }
        val use = text.getValue("b/Use.java")
        assertTrue("x.Target t" in use, "fq ref + ctor: $use")
        assertTrue("doIt()" in use, "method call: $use")
        assertTrue("mappedField" in use, "field access: $use")
        val targetSrc = text.getValue("x/Target.java")
        assertTrue("mappedField" in targetSrc, "field decl: $targetSrc")
        assertTrue("doIt" in targetSrc, "method decl: $targetSrc")
    }

    /** path-2 shape: sources already in target space get metadata inserts —
     *  `implements` injection + javadoc — via [SourceExtension]s. */
    @Test
    fun metadataStages() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "x/Target.java" to """
                package x;
                public class Target {
                    public int mappedField;
                    public void doIt() {}
                }
            """.trimIndent(),
        ))

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.field("f_1;I", "mappedField;I")
        target.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.ADD,
            xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature.unchecked("Lx/Iface;"),
            named))
        target.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement("a target", named))
        target.method("m_1;()V", "doIt;()V").addMetadata(
            xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement("does it", named))

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(
                xyz.wagyourtail.unimined.mapping.remapper.source.psi.InterfaceInjectionExtension(),
                xyz.wagyourtail.unimined.mapping.remapper.source.psi.JavadocExtension(),
            )))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("x/Target.java")).readBytes().decodeToString()
        }
        assertTrue("implements x.Iface" in text, "iface injection: $text")
        assertTrue("a target" in text, "class javadoc: $text")
        assertTrue("does it" in text, "method javadoc: $text")
    }

    /** linemap application: `0x4646` extras on a sources jar rewrite
     *  LineNumberNodes in the matching class entries. */
    @Test
    fun linemapApply() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "x/Target.java" to """
                package x;
                public class Target {
                    public void doIt() {
                        int i = 1;
                        int j = 2;
                    }
                }
            """.trimIndent(),
        ))

        // put a linemap extra field on the source entry — decompiled line → orig line
        val lmExtra = xyz.wagyourtail.unimined.mapping.remapper.source.Linemap
            .replace(null, mapOf(4 to 40, 5 to 50, 6 to 60))
        val linemappedSrc = dir.resolve("src-lm.jar")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(linemappedSrc)).use { zout ->
            java.util.zip.ZipFile(srcJar.toFile()).use { zin ->
                for (e in zin.entries().asSequence()) {
                    zout.putNextEntry(java.util.zip.ZipEntry(e.name).apply {
                        time = 0L
                        if (e.name == "x/Target.java") setExtra(lmExtra)
                    })
                    zout.write(zin.getInputStream(e).readBytes())
                    zout.closeEntry()
                }
            }
        }

        val out = dir.resolve("linemapped.jar")
        xyz.wagyourtail.unimined.mapping.remapper.source.Linemap.applyJar(binJar, linemappedSrc, out)

        val node = readClasses(out).getValue("x/Target")
        val lines = node.methods.first { it.name == "doIt" }.instructions.toArray()
            .filterIsInstance<org.objectweb.asm.tree.LineNumberNode>().map { it.line }
        assertTrue(40 in lines || 50 in lines || 60 in lines, "linemapped: $lines")
        assertTrue(lines.none { it in 4..6 }, "originals gone: $lines")
    }

    private val mixinStubs = mapOf(
        "org/spongepowered/asm/mixin/Mixin.java" to
            "package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; }",
        "org/spongepowered/asm/mixin/Shadow.java" to
            "package org.spongepowered.asm.mixin; public @interface Shadow { String prefix() default \"shadow$\"; String[] aliases() default {}; }",
        "org/spongepowered/asm/mixin/injection/Inject.java" to
            "package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); At[] at(); }",
        "org/spongepowered/asm/mixin/injection/At.java" to
            "package org.spongepowered.asm.mixin.injection; public @interface At { String value(); String target() default \"\"; }",
        "org/spongepowered/asm/mixin/gen/Invoker.java" to
            "package org.spongepowered.asm.mixin.gen; public @interface Invoker { String value() default \"\"; String prefix() default \"call\"; }",
        "org/spongepowered/asm/mixin/gen/Accessor.java" to
            "package org.spongepowered.asm.mixin.gen; public @interface Accessor { String value() default \"\"; String prefix() default \"get\"; }",
    )

    /** non-refmap mode: `@Shadow` member names follow the target's mapped
     *  name and annotation params remap — the same semantics the bytecode
     *  side applies via [AnnotationTable]. */
    @Test
    fun mixinSourceStage() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mixinStubs + mapOf(
            "a/Target.java" to "package a; public class Target { public int f_1; public void m_1() {} }",
            "m/TargetMixin.java" to """
                package m;
                import org.spongepowered.asm.mixin.Mixin;
                import org.spongepowered.asm.mixin.Shadow;
                import org.spongepowered.asm.mixin.injection.Inject;
                import org.spongepowered.asm.mixin.injection.At;
                @Mixin(targets = "a.Target")
                public class TargetMixin {
                    @Shadow public int f_1;
                    @Inject(method = "m_1()V", at = @At("HEAD"))
                    public void handler() {}
                }
            """.trimIndent(),
        ))

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.field("f_1;I", "mappedField;I")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(
                xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinSourceExtension(),
            )))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("m/TargetMixin.java")).readBytes().decodeToString()
        }
        assertTrue("doIt()V" in text, "@Inject method: $text")
        assertTrue("mappedField" in text, "@Shadow rename: $text")
        assertTrue("x.Target" in text || "x/Target" in text, "targets: $text")
    }

    /** a transform that *does* insert lines must shift vineflower linemap
     *  extras to match — the deltas flow engine → Result.extras. */
    @Test
    fun linemapAdjustment() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "x/Target.java" to "package x;\npublic class Target {\n    public void doIt() {}\n}",
        ))

        // attach a linemap extra: decompiled lines 2-4 → original lines
        val lm = xyz.wagyourtail.unimined.mapping.remapper.source.Linemap
            .replace(null, mapOf(2 to 20, 3 to 30, 4 to 40))
        val srcJar2 = dir.resolve("src-lm.jar")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(srcJar2)).use { zout ->
            java.util.zip.ZipFile(srcJar.toFile()).use { zin ->
                for (e in zin.entries().asSequence()) {
                    zout.putNextEntry(java.util.zip.ZipEntry(e.name).apply {
                        time = 0L
                        if (e.name == "x/Target.java") setExtra(lm)
                    })
                    zout.write(zin.getInputStream(e).readBytes())
                    zout.closeEntry()
                }
            }
        }

        // extension that prepends a one-line header — every line shifts +1
        val headerStage = xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension { file, ctx, changes, _ ->
            changes.insert(0, "// generated\n")
        }

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar2, out, treeOf(), obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(headerStage)))

        val extraOut = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getEntry("x/Target.java").extra
        }
        val shifted = xyz.wagyourtail.unimined.mapping.remapper.source.Linemap.parse(extraOut).orEmpty()
        assertTrue(shifted[3] == 20 && shifted[4] == 30 && shifted[5] == 40, "shifted: $shifted")
    }

    /** wildcard imports must expand to explicit imports of the mapped
     *  classes — otherwise renamed-package refs go unresolved. */
    @Test
    fun wildcardImportExpansion() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/Target.java" to "package a; public class Target { public void m_1() {} }",
            "b/Use.java" to """
                package b;
                import a.*;
                public class Use {
                    void go() { new Target().m_1(); }
                }
            """.trimIndent(),
        ))
        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("b/Use.java")).readBytes().decodeToString()
        }
        assertTrue("import a.*" !in text, "wildcard gone: $text")
        assertTrue("import x.Target" in text, "explicit import: $text")
    }

    /** the same mixin semantics over `KtAnnotationEntry`/Kt decls —
     *  `method=` strings remap and `@Shadow` properties rename. */
    @Test
    fun kotlinMixinSource() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mixinStubs + mapOf(
            "a/Target.java" to "package a; public class Target { public int f_1; public void m_1() {} }",
        ))
        // add the .kt mixin source — parsed+analyzed by the engine, no compile needed
        val ktSrc = dir.resolve("src2.jar")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(ktSrc)).use { zout ->
            java.util.zip.ZipFile(srcJar.toFile()).use { zin ->
                for (e in zin.entries().asSequence()) {
                    zout.putNextEntry(java.util.zip.ZipEntry(e.name).apply { time = 0L })
                    zout.write(zin.getInputStream(e).readBytes()); zout.closeEntry()
                }
            }
            zout.putNextEntry(java.util.zip.ZipEntry("m/TargetMixin.kt").apply { time = 0L })
            zout.write("""package m
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(targets = ["a.Target"])
class TargetMixin {
    @Shadow var f_1: Int = 0

    @Inject(method = ["m_1()V"], at = [At("HEAD")])
    fun handler() {}

    @Invoker("m_1")
    fun inv_1() = Unit

    fun caller() = inv_1()
}
""".toByteArray())
            zout.closeEntry()
        }

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.field("f_1;I", "mappedField;I")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(ktSrc, out, tree, obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(
                xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinSourceExtension(),
            )))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("m/TargetMixin.kt")).readBytes().decodeToString()
        }
        assertTrue("doIt()V" in text, "kt @Inject method: $text")
        assertTrue("mappedField" in text, "kt @Shadow rename: $text")
        assertTrue("fun doIt()" in text, "invoker decl renamed: $text")
        assertTrue("inv_1" !in text.replace("inv_1() = Unit","").replace("fun inv_1",""), "call ref renamed: $text")
    }

    /** a class moving packages relocates its source file and rewrites the
     *  package declaration — otherwise remapped sources won't compile. */
    @Test
    fun fileRelocation() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/Target.java" to "package a; public class Target { public void m_1() {} }",
        ))
        val tree = treeOf()
        tree.cls("a/Target", "x/y/Target")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar))

        val names = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.entries().asSequence().map { it.name }.toList()
        }
        assertTrue("x/y/Target.java" in names, "relocated: $names")
        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("x/y/Target.java")).readBytes().decodeToString()
        }
        assertTrue("package x.y;" in text, "package decl: $text")
    }

    /** package-info.java relocates via the package-info.class mapping —
     *  no class decls to anchor the package mapping on. */
    @Test
    fun packageInfoRelocation() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/package-info.java" to "package a;",
            "x/Target.java" to "package x; public class Target {}",
        ))
        val tree = treeOf()
        tree.cls("a/package-info", "x/package-info")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar))

        val names = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.entries().asSequence().map { it.name }.toList()
        }
        assertTrue("x/package-info.java" in names, "relocated: $names")
        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("x/package-info.java")).readBytes().decodeToString()
        }
        assertTrue("package x;" in text, "pkg decl: $text")
    }

    /** kt property/ctor-param/class decl renames via backing field + fq class */
    @Test
    fun kotlinDeclRemap() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/Target.java" to "package a; public class Target { public int f_1; }",
        ))
        val ktSrc = dir.resolve("src2.jar")
        java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(ktSrc)).use { zout ->
            zout.putNextEntry(java.util.zip.ZipEntry("m/Use.kt").apply { time = 0L })
            zout.write("""package m
/** see [a.Target#f_1] */
class Holder(val f_1: Int) {
    var other = f_1
    fun use() = a.Target().f_1
}
""".toByteArray())
            zout.closeEntry()
        }
        val tree = treeOf()
        val t = tree.cls("a/Target", "x/Target")
        t.field("f_1;I", "mappedField;I")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(ktSrc, out, tree, obf, named, classpath = listOf(binJar))
        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("m/Use.kt")).readBytes().decodeToString()
        }
        assertTrue("mappedField" in text, "property + ref: $text")
        assertTrue("x.Target" in text || "Target()" in text, "class ref: $text")
    }

    /** record components map via the field row — one decl site, but the
     *  accessor + backing field names follow it. */
    @Test
    fun recordComponentRemap() {
        if (System.getProperty("java.specification.version").removePrefix("1.").toInt() < 16) return
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "a/Rec.java" to "package a; public record Rec(int f_1) {}",
        ))
        val tree = treeOf()
        tree.cls("a/Rec", "x/Rec").field("f_1;I", "mappedField;I")

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar))
        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("x/Rec.java")).readBytes().decodeToString()
        }
        assertTrue("mappedField" in text, "component: $text")
    }

    /** unpick — literal in a targeted method call → constant ref expression */
    @Test
    fun unpickLiterals() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            "d/Dir.java" to "package d; public class Dir { public static final int NORTH = 0; public static final int SOUTH = 1; }",
            "a/Use.java" to """
                package a;
                public class Use {
                    void setDir(int dir) {}
                    void go() { setDir(0); setDir(1); int x = 5; }
                }
            """.trimIndent(),
        ))
        val tree = treeOf()
        val group = xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping(
            xyz.wagyourtail.unimined.mapping.element.InlineType.PLAIN,
            name = "dir",
            baseNs = obf,
        )
        val dirCls = xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked("d/Dir")
        group.constant(dirCls, xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("NORTH"), null)
        group.constant(dirCls, xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("SOUTH"), null)
        group.expression(
            xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant.unchecked("0"),
            xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression.unchecked("this.NORTH"))
        group.expression(
            xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant.unchecked("1"),
            xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression.unchecked("this.SOUTH"))
        group.target(
            xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName.unchecked("La/Use;setDir(I)V"), null)
        (tree.constantGroups as MutableList).add(group)

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(
                xyz.wagyourtail.unimined.mapping.remapper.source.psi.UnpickSourceExtension(),
            )))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("a/Use.java")).readBytes().decodeToString()
        }
        assertTrue("setDir(d.Dir.NORTH)" in text && "setDir(d.Dir.SOUTH)" in text,
            "unpicked: $text")
        assertTrue("int x = 5;" in text, "non-target literal untouched: $text")
    }

    /** source AW — AccessElements rewrite modifiers in source text */
    @Test
    fun accessSourceRemap() {
        val dir = createTempDirectory()
        val (srcJar, binJar) = buildPair(dir, mapOf(
            // path-2 shape: source already in target (named) space
            "x/Target.java" to "package x; public class Target { private int f; private void m() {} }",
        ))
        val tree = treeOf()
        val cls = tree.cls("a/Target", "x/Target")
        val field = cls.field("f;I", "f;I")
        field.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.ADD,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PUBLIC,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))
        field.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.REMOVE,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PRIVATE,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))
        val method = cls.method("m;()V", "m;()V")
        method.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.ADD,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PUBLIC,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))
        method.addMetadata(xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement(
            xyz.wagyourtail.unimined.mapping.element.AddRemove.REMOVE,
            xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag.PRIVATE,
            xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL))

        val out = dir.resolve("out.jar")
        SourceRemapper.remap(srcJar, out, tree, obf, named, classpath = listOf(binJar),
            options = xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions(extensions = listOf(
                xyz.wagyourtail.unimined.mapping.remapper.source.psi.AccessSourceExtension(),
            )))

        val text = java.util.zip.ZipFile(out.toFile()).use { z ->
            z.getInputStream(z.getEntry("x/Target.java")).readBytes().decodeToString()
        }
        assertTrue("public int f" in text && "public void m" in text, "public added: $text")
        assertTrue("private int f" !in text && "private void m" !in text, "private gone: $text")
    }
}
