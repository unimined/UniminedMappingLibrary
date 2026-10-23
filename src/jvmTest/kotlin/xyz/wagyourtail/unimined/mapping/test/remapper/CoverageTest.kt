package xyz.wagyourtail.unimined.mapping.test.remapper

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.AddRemoveClear
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.field
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readEntries
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.voidMethod
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import java.util.jar.Attributes
import java.util.jar.Manifest
import kotlin.io.path.createTempDirectory
import kotlin.metadata.jvm.signature
import kotlin.test.*

/** Remaining-arm coverage: signature injection, REMOVE/MODIFY arms, bridge
 *  opcode variants, LV option details, in-memory entry point, passthrough. */
class CoverageTest {

    // --- SignatureElement: element *writes* the Signature attr (distinct from remapping an existing one) ---

    @Test
    fun injectsSignatureAttributes() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        cls.addMetadata(SignatureElement(ClassSignature.unchecked("<T:Leqm;>Ljava/lang/Object;"), obf))
        val f = cls.field("field_1;I", "mappedField;I")
        (f as xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping)
            .addMetadata(SignatureElement(FieldSignature.unchecked("Leqm;"), obf))

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            fields.add(FieldNode(Opcodes.ACC_PRIVATE, "field_1", "I", null, null))
        }))
        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        assertEquals("<T:Lnet/minecraft/Example;>Ljava/lang/Object;", out.signature)
        assertEquals("Lnet/minecraft/Example;", out.fields.first { it.name == "mappedField" }.signature)
    }

    // --- REMOVE/MODIFY arms ---

    @Test
    fun removesInterfaceExceptionSealEntries() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        cls.addMetadata(InterfaceElement(AddRemove.REMOVE, ClassTypeSignatureOf("Ltest/Old;"), named))
        cls.addMetadata(SealElement(AddRemoveClear.REMOVE, InternalName.unchecked("test/A"), named))
        val m = cls.method("method_1;()V", "mappedMethod;()V") as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
        m.addMetadata(ExceptionElement(AddRemove.REMOVE, InternalName.unchecked("java/io/IOException"), named))

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            interfaces.add("test/Old")
            permittedSubclasses = mutableListOf("test/A", "test/B")
            methods.add(voidMethod("method_1").apply { exceptions.add("java/io/IOException") })
        }))
        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        assertEquals(emptyList(), out.interfaces.toList())
        assertEquals(listOf("test/B"), out.permittedSubclasses)
        assertEquals(emptyList(), out.methods.first { it.name == "mappedMethod" }.exceptions.toList())
    }

    private fun ClassTypeSignatureOf(v: String) =
        xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature.unchecked(v)

    @Test
    fun modifiesAndRemovesAnnotations() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        cls.addMetadata(AnnotationElement(AddRemoveModify.MODIFY, named,
            Annotation.unchecked("@Ltest/Marker;(value=99)")))
        cls.addMetadata(AnnotationElement(AddRemoveModify.REMOVE, named,
            Annotation.unchecked("@Ltest/Gone;()")))

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            visibleAnnotations = mutableListOf(
                AnnotationNode("Ltest/Marker;").apply { visit("value", 1); visit("other", 5); visitEnd() },
                AnnotationNode("Ltest/Gone;").apply { visitEnd() },
            )
        }))
        AsmRemapper.remap(input, output, tree, obf, named)

        val anns = readClasses(output).getValue("net/minecraft/Example").visibleAnnotations
        assertNull(anns.firstOrNull { it.desc == "Ltest/Gone;" })
        val marker = anns.first { it.desc == "Ltest/Marker;" }
        // MODIFY merges: value overwritten, other preserved
        assertEquals(listOf("value", 99, "other", 5), marker.values)
    }

    // --- BridgeEmitter opcode variants + dup guard ---

    @Test
    fun bridgeOpcodeVariants() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        (cls.method("priv;()V", "priv;()V") as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping)
            .addMetadata(SyntheticBridgeElement(mapOf(named to UnqualifiedName.unchecked("privAlias"))))
        (cls.method("stat;()V", "stat;()V") as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping)
            .addMetadata(SyntheticBridgeElement(mapOf(named to UnqualifiedName.unchecked("statAlias"))))
        val itf = tree.cls("pkg/Itf", "pkg/Itf")
        (itf.method("call;()V", "call;()V") as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping)
            .addMetadata(SyntheticBridgeElement(mapOf(named to UnqualifiedName.unchecked("callAlias"))))

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") {
                methods.add(voidMethod("priv", Opcodes.ACC_PRIVATE))
                methods.add(voidMethod("stat", Opcodes.ACC_STATIC))
            },
            "pkg/Itf.class" to classBytes("pkg/Itf", Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "call", "()V", null, null))
            },
        ))
        AsmRemapper.remap(input, output, tree, obf, named)

        val classes = readClasses(output)
        val privBridge = classes.getValue("net/minecraft/Example").methods.first { it.name == "privAlias" }
        assertEquals(Opcodes.INVOKESPECIAL, privBridge.delegateOpcode())
        val statBridge = classes.getValue("net/minecraft/Example").methods.first { it.name == "statAlias" }
        assertEquals(Opcodes.INVOKESTATIC, statBridge.delegateOpcode())
        val itfBridge = classes.getValue("pkg/Itf").methods.first { it.name == "callAlias" }
        assertEquals(Opcodes.INVOKEINTERFACE, itfBridge.delegateOpcode())
    }

    private fun MethodNode.delegateOpcode(): Int =
        instructions.toArray().filterIsInstance<MethodInsnNode>().single().opcode

    @Test
    fun bridgeDupNameGuard() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val cls = tree.cls("eqm", "net/minecraft/Example")
        (cls.method("method_1;()V", "mappedMethod;()V") as xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping)
            .addMetadata(SyntheticBridgeElement(mapOf(named to UnqualifiedName.unchecked("taken"))))

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            methods.add(voidMethod("method_1"))
            // a real method already occupies the alias slot
            methods.add(MethodNode(Opcodes.ACC_PRIVATE, "taken", "()V", null, null).apply {
                visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
            })
        }))
        AsmRemapper.remap(input, output, tree, obf, named)

        val out = readClasses(output).getValue("net/minecraft/Example")
        val taken = out.methods.filter { it.name == "taken" }
        assertEquals(1, taken.size, "alias collision should skip bridge emission")
        assertEquals(0, taken.first().access and Opcodes.ACC_BRIDGE)
    }

    // --- LV option details ---

    @Test
    fun inferNameFromSameLvIndexReusesSiblingName() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null).apply {
                visitCode()
                val l0 = Label(); val l1 = Label()
                visitLabel(l0); visitInsn(Opcodes.ICONST_1); visitVarInsn(Opcodes.ISTORE, 1)
                visitInsn(Opcodes.RETURN); visitLabel(l1)
                // two LVT entries, same slot+desc: invalid + valid sibling
                visitLocalVariable("0x", "I", null, l0, l1, 1)
                visitLocalVariable("count", "I", null, l0, l1, 1)
                visitMaxs(1, 2); visitEnd()
            })
        }))
        AsmRemapper.remap(input, output, tree, obf, named,
            Options(renameInvalidLocals = true, inferNameFromSameLvIndex = true))

        val lvs = readClasses(output).getValue("net/minecraft/Example")
            .methods.first { it.name == "m" }.localVariables.map { it.name }
        assertEquals(listOf("count", "count"), lvs)
    }

    @Test
    fun invalidLvNamePatternOverridesDefault() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf("eqm.class" to classBytes("eqm") {
            methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m", "()V", null, null).apply {
                visitCode()
                val l0 = Label(); val l1 = Label()
                visitLabel(l0); visitInsn(Opcodes.ICONST_1); visitVarInsn(Opcodes.ISTORE, 1)
                visitInsn(Opcodes.RETURN); visitLabel(l1)
                visitLocalVariable("x_junk", "I", null, l0, l1, 1) // valid identifier, pattern-invalid
                visitMaxs(1, 2); visitEnd()
            })
        }))
        AsmRemapper.remap(input, output, tree, obf, named,
            Options(renameInvalidLocals = true, invalidLvNamePattern = Regex("x_.*")))

        val lv = readClasses(output).getValue("net/minecraft/Example")
            .methods.first { it.name == "m" }.localVariables.first { it.index == 1 }
        assertTrue(!lv.name.startsWith("x_"), "pattern-invalid name should be regenerated: ${lv.name}")
    }

    // --- in-memory entry point + passthrough ---

    @Test
    fun inMemoryRemapReturnsClassNodes() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar")

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf(
            "eqm.class" to classBytes("eqm") { methods.add(voidMethod("method_1")) },
            "data.txt" to "verbatim".toByteArray(),
        ))

        val result = AsmRemapper.remap(input, tree, obf, named)
        val cls = result.classes.single()
        assertEquals("net/minecraft/Example.class", cls.first)
        assertEquals("net/minecraft/Example", cls.second.name)
        assertEquals(listOf("data.txt"), result.resources.map { it.first })
        assertEquals("verbatim", result.resources.single().second.toString(Charsets.UTF_8))
    }

    @Test
    fun unmappedClassesAndPackageFallbackPassThrough() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.package_(mapOf(obf to PackageName.unchecked("com/old/"), named to PackageName.unchecked("com/new/")))

        writeJar(input, mapOf(
            "unmapped/Cls.class" to classBytes("unmapped/Cls"),
            "com/old/Foo.class" to classBytes("com/old/Foo"),
        ))
        AsmRemapper.remap(input, output, tree, obf, named)

        val classes = readClasses(output)
        assertNotNull(classes["unmapped/Cls"], "unmapped class should pass through")
        assertNotNull(classes["com/new/Foo"], "unmapped class in mapped package should remap package part")
    }

    @Test
    fun dropsManifestDigestSections() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.entries["some.class"] = Attributes().apply { putValue("SHA-256-Digest", "abc") }
        manifest.entries["other.class"] = Attributes().apply { putValue("Foo", "bar") }

        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        writeJar(input, mapOf("eqm.class" to classBytes("eqm")), manifest)
        AsmRemapper.remap(input, output, tree, obf, named)

        java.util.zip.ZipFile(output.toFile()).use { zin ->
            val mf = Manifest(zin.getInputStream(zin.getEntry("META-INF/MANIFEST.MF")))
            assertNull(mf.entries["some.class"], "digest section should be dropped")
            assertNotNull(mf.entries["other.class"])
        }
    }



    /** [AsmExtension] lifecycle — analyze all inputs, transform output nodes,
     *  and append raw entries (the refmap use-case). */
    @Test
    fun extensionHooks() {
        val input = createTempDirectory().resolve("in.jar")
        val out = createTempDirectory().resolve("out.jar")
        writeJar(input, mapOf("pkg/A.class" to classBytes("pkg/A")))
        val tree = treeOf()
        tree.cls("eqm", "net/minecraft/Example")

        val seen = mutableListOf<String>()
        var finished = false
        val options = Options(extensions = listOf(xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
            object : xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension {
                override fun analyze(node: org.objectweb.asm.tree.ClassNode) { seen += node.name }
                override fun finishAnalysis() { finished = true }
                override fun postApply(input: org.objectweb.asm.tree.ClassNode, output: org.objectweb.asm.tree.ClassNode, cls: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
                    if (output.name == ctx.mapClass("eqm")) output.version = 60 // arbitrary mutation lands
                }
                override fun resources() = listOf("custom/refmap.json" to "{}".toByteArray())
            }
        }))
        AsmRemapper.remap(input, out, tree, obf, named, options)

        assertEquals(listOf("pkg/A"), seen, "analyze should see every input class")
        assertTrue(finished)
        assertTrue("custom/refmap.json" in readEntries(out), "extension resource emitted")
        // 'eqm' isn't in this input jar — postApply just shouldn't fire for it;
        // the emitted resource + lifecycle ordering are what we're testing
        val eqmExt = xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
            object : xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension {
                override fun postApply(i: org.objectweb.asm.tree.ClassNode, o: org.objectweb.asm.tree.ClassNode, c: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
                    if (o.name == "pkg/A") o.version = 60
                }
            }
        }
        val out2 = createTempDirectory().resolve("out2.jar")
        AsmRemapper.remap(input, out2, tree, obf, named, Options(extensions = listOf(eqmExt)))
        assertEquals(60, readClasses(out2).getValue("pkg/A").version)
    }


    /** invokedynamic + MethodHandle constants, NestHost/NestMembers, and the
     *  enclosing-method ref all remap through ClassRemapper's free arms. */
    @Test
    fun indyNestAndEnclosingMethodRefs() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("a/Host", "x/Host")
        tree.cls("a/Inner", "x/Inner")
        tree.cls("a/Lib", "x/Lib")
            .method("m_1;()V", "run;()V")

        writeJar(input, mapOf(
            "a/Host.class" to classBytes("a/Host") {
                nestMembers = listOf("a/Inner")   // nest member list — remaps
            },
            "a/Inner.class" to classBytes("a/Inner") {
                nestHostClass = "a/Host"          // nest host backref — remaps
            },
            "a/Lib.class" to classBytes("a/Lib") {
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m_1", "()V", null, null).apply {
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
            // b/Anon is an anonymous class inside Lib.run — enclosing-method ref
            "a/Lib$1.class" to classBytes("a/Lib\$1") {
                outerClass = "a/Lib"; outerMethod = "m_1"; outerMethodDesc = "()V"
                innerClasses.add(InnerClassNode("a/Lib\$1", "a/Lib", null, 0))
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null).apply {
                    visitCode()
                    // indy referencing Lib.run via a bootstrap handle
                    visitInvokeDynamicInsn("x", "()V",
                        org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "a/Lib", "m_1", "()V", false))
                    visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
        ))

        AsmRemapper.remap(input, output, tree, obf, named)

        val out0 = readClasses(output)
        assertEquals("x/Host", out0.getValue("x/Inner").nestHostClass)
        assertEquals(listOf("x/Inner"), out0.getValue("x/Host").nestMembers)
        val anon = out0.getValue("a/Lib\$1")
        assertEquals("run", anon.outerMethod, "enclosing-method name should remap")
        assertEquals("x/Lib", anon.outerClass)
        // indy bootstrap handle remapped too
        val indy = anon.methods.first { it.name == "call" }.instructions
            .asSequence().filterIsInstance<org.objectweb.asm.tree.InvokeDynamicInsnNode>().single()
        assertEquals("x/Lib", indy.bsm.owner)
        assertEquals("run", indy.bsm.name)
    }


    /** declarative annotation remapping: a table maps annotation params to
     *  remap kinds; shadow-annotated members rename to the mixin target's name. */
    @Test
    fun annotationTableExtension() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("a/Target", "x/Target").method("m_1;()V", "doIt;()V")

        val table = xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.annotationTable {
            annotation("Ltest/Inject;") {
                param("method", xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap.MethodRef)
            }
            annotation("Ltest/Mixin;") {
                param("targets", xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap.ClassRef)
            }
            mixinMarker("Ltest/Mixin;")
            shadow("Ltest/Shadow;")
        }
        val ext = xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
            xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension(ctx, table)
        }

        writeJar(input, mapOf(
            "a/Target.class" to classBytes("a/Target") {
                methods.add(voidMethod("m_1"))
            },
            "b/Mix.class" to classBytes("b/Mix") {
                visibleAnnotations = listOf(AnnotationNode("Ltest/Mixin;").apply {
                    values = listOf("targets", listOf("a/Target"))
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m_1", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Ltest/Shadow;"))
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "call", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Ltest/Inject;").apply {
                        values = listOf("method", "m_1()V")
                    })
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
        ))


        AsmRemapper.remap(input, output, tree, obf, named, Options(extensions = listOf(ext)))

        val mix = readClasses(output).getValue("b/Mix")
        // @Mixin targets value remapped
        val mixinAnn = mix.visibleAnnotations.single()
        assertTrue(mixinAnn.values.toString().contains("x/Target"), "mixin values=${mixinAnn.values}")
        // @Inject method value remapped through the table
        val inject = mix.methods.first { it.name == "call" }.visibleAnnotations.single()
        assertTrue("doIt()V" in inject.values.toString(), "values=${inject.values}")
        // shadow method renamed to the target's named name
        assertTrue(mix.methods.any { it.name == "doIt" }, "methods=${mix.methods.map { it.name }}")
    }

    /** mixin extension: in-place annotation remap + refmap emission driven by
     *  the jar's mixin config; MixinExtras annotations remap through the same
     *  table; existing refmap entries merge into the output. */
    @Test
    fun mixinExtensionEmitsRefmap() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.field("f_1;I", "mappedField;I")

        val mixinsJson = """{"package":"b","mixins":["Mix"],"refmap":"mod-refmap.json"}""".encodeToByteArray()

        writeJar(input, mapOf(
            "mod.mixins.json" to mixinsJson,
            "a/Target.class" to classBytes("a/Target") {
                methods.add(voidMethod("m_1"))
                fields.add(FieldNode(Opcodes.ACC_PRIVATE, "f_1", "I", null, null))
            },
            "b/Mix.class" to classBytes("b/Mix") {
                visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;").apply {
                    values = listOf("targets", listOf("a/Target"))
                })
                // @Shadow member — renamed + recorded
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m_1", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;"))
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                // MixinExtras injector — `method` ref rewritten + recorded
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "wrap", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;").apply {
                        values = listOf("method", "m_1()V")
                    })
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                // @Accessor — prefix-derived rename + recorded
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "getF_1", "()I", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/gen/Accessor;"))
                    visitCode(); visitInsn(Opcodes.ICONST_0); visitInsn(Opcodes.IRETURN); visitMaxs(1, 1); visitEnd()
                })
            },
        ))
        AsmRemapper.remap(input, output, tree, obf, named, Options(extensions = listOf(
            xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
                xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension(ctx)
            }
        )))

        val mix = readClasses(output).getValue("b/Mix")
        // in-place: WrapOperation method ref + shadow/accessor renames
        val wrap = mix.methods.first { it.name == "wrap" }.visibleAnnotations.single()
        assertTrue("doIt()V" in wrap.values.toString(), "wrap values=${wrap.values}")
        assertTrue(mix.methods.any { it.name == "doIt" }, "shadow rename: ${mix.methods.map { it.name }}")
        assertTrue(mix.methods.any { it.name == "getMappedField" }, "accessor rename: ${mix.methods.map { it.name }}")

        // refmap emitted at the config's declared path
        val refmapBytes = java.util.zip.ZipFile(output.toFile()).use {
            it.getInputStream(it.getEntry("mod-refmap.json")).readBytes().decodeToString()
        }
        val refmap = kotlinx.serialization.json.Json.parseToJsonElement(refmapBytes).jsonObject
        val mixEntries = refmap.getValue("mappings").jsonObject.getValue("b/Mix").jsonObject
        // canonical target-space refs keyed by the written value
        assertEquals("Lx/Target;doIt()V", mixEntries["m_1()V"]?.jsonPrimitive?.content)
        assertEquals("x/Target", mixEntries["a/Target"]?.jsonPrimitive?.content)
        assertTrue(mixEntries.containsKey("m_1"), "shadow entry: $mixEntries")
        assertEquals("Lx/Target;mappedField:I", mixEntries["f_1"]?.jsonPrimitive?.content)
    }

    /** configs that declare no refmap get patched to point at the emitted one */
    @Test
    fun mixinConfigWithoutRefmapPatched() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        tree.cls("a/Target", "x/Target").method("m_1;()V", "doIt;()V")

        writeJar(input, mapOf(
            "mod.mixins.json" to """{"package":"b","mixins":["Mix"]}""".encodeToByteArray(),
            "a/Target.class" to classBytes("a/Target") { methods.add(voidMethod("m_1")) },
            "b/Mix.class" to classBytes("b/Mix") {
                visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;").apply {
                    values = listOf("targets", listOf("a/Target"))
                })
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m_1", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Lorg/spongepowered/asm/mixin/Shadow;"))
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
        ))
        AsmRemapper.remap(input, output, tree, obf, named, Options(extensions = listOf(
            xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
                xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinExtension(ctx)
            }
        )))

        java.util.zip.ZipFile(output.toFile()).use { zin ->
            val config = zin.getInputStream(zin.getEntry("mod.mixins.json")).readBytes().decodeToString()
            assertTrue("mod-refmap.json" in config, "patched config: $config")
            assertNotNull(zin.getEntry("mod-refmap.json"), "expected generated refmap")
        }
    }

    /** `@Shadow(prefix=…)` strips the prefix for the target lookup and keeps
     *  it on rename; `aliases` supplies fallback target names. */
    @Test
    fun shadowPrefixAndAliases() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        val tree = treeOf()
        val target = tree.cls("a/Target", "x/Target")
        target.method("m_1;()V", "doIt;()V")
        target.method("m_2;()V", "otherName;()V")

        val table = xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.annotationTable {
            annotation("Ltest/Mixin;") { param("targets", xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap.ClassRef) }
            mixinMarker("Ltest/Mixin;")
            shadow("Ltest/Shadow;")
        }
        writeJar(input, mapOf(
            "a/Target.class" to classBytes("a/Target") {
                methods.add(voidMethod("m_1")); methods.add(voidMethod("m_2"))
            },
            "b/Mix.class" to classBytes("b/Mix") {
                visibleAnnotations = listOf(AnnotationNode("Ltest/Mixin;").apply {
                    values = listOf("targets", listOf("a/Target"))
                })
                // prefixed shadow: member `m$m_1` targets `m_1` → `m$doIt`
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "m\$m_1", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Ltest/Shadow;").apply {
                        values = listOf("prefix", "m$")
                    })
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
                // aliased shadow: member `helper` targets alias `m_2` → `otherName`
                methods.add(MethodNode(Opcodes.ACC_PUBLIC, "helper", "()V", null, null).apply {
                    visibleAnnotations = listOf(AnnotationNode("Ltest/Shadow;").apply {
                        values = listOf("aliases", listOf("m_2"))
                    })
                    visitCode(); visitInsn(Opcodes.RETURN); visitMaxs(0, 1); visitEnd()
                })
            },
        ))
        AsmRemapper.remap(input, output, tree, obf, named, Options(extensions = listOf(
            xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
                xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension(ctx, table)
            }
        )))

        val names = readClasses(output).getValue("b/Mix").methods.map { it.name }
        assertTrue("m\$doIt" in names, "prefixed shadow rename: $names")
        assertTrue("otherName" in names, "aliased shadow rename: $names")
    }


    /** kotlin.Metadata annotation contents remap: classifiers, member names,
     *  and jvm signatures inside the Km model follow the mapping tree. */
    @Test
    fun kotlinMetadataExtension() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val output = dir.resolve("out.jar")

        // real kotlin.Metadata from kotlin.Pair — valid model, names remapped by the extension
        val md = kotlin.Pair::class.java.getAnnotation(kotlin.Metadata::class.java)
        val srcMd = kotlin.metadata.jvm.KotlinClassMetadata.readStrict(md) as kotlin.metadata.jvm.KotlinClassMetadata.Class
        val srcFn = srcMd.kmClass.functions.first().name
        val srcSig = srcMd.kmClass.functions.first().signature?.descriptor

        val tree2 = treeOf()
        tree2.cls("kotlin/Pair", "x/Pair").method("$srcFn;$srcSig", "renamed;$srcSig")

        val ext = xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtensionFactory { ctx ->
            xyz.wagyourtail.unimined.mapping.remapper.ext.metadata.KotlinMetadataExtension(ctx)
        }

        writeJar(input, mapOf(
            "kotlin/Pair.class" to classBytes("kotlin/Pair") {
                visibleAnnotations = listOf(AnnotationNode("Lkotlin/Metadata;").apply {
                    values = listOf(
                        "k", md.kind, "mv", md.metadataVersion.toList(), "bv", md.bytecodeVersion.toList(),
                        "d1", md.data1.toList(), "d2", md.data2.toList(),
                        "xs", md.extraString, "pn", md.packageName, "xi", md.extraInt)
                })
            },
        ))

        AsmRemapper.remap(input, output, tree2, obf, named, Options(extensions = listOf(ext)))

        val node = readClasses(output).getValue("x/Pair")
        val ann = node.visibleAnnotations.single { it.desc == "Lkotlin/Metadata;" }
        val v = ann.values
        fun <T> get(key: String): T = v[v.indexOf(key) + 1] as T
        val outMd = kotlin.Metadata(
            get<Int>("k"), get<List<Int>>("mv").toIntArray(), get<List<Int>>("bv").toIntArray(),
            (get<List<String>>("d1")).toTypedArray(), (get<List<String>>("d2")).toTypedArray(),
            get<String>("xs"), get<String>("pn"), get<Int>("xi"))
        val out = kotlin.metadata.jvm.KotlinClassMetadata.readStrict(outMd) as kotlin.metadata.jvm.KotlinClassMetadata.Class
        assertEquals("x/Pair", out.kmClass.name)
        assertEquals("renamed", out.kmClass.functions.first().name)
    }

}
