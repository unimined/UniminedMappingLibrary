package xyz.wagyourtail.unimined.mapping.test.remapper

import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.classBytes
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.readClasses
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.writeJar
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Invokedynamic callsite-name remapping: lambda callsite names are the
 * functional interface's SAM name and must follow its mapping; bsm handle
 * + args (including [ConstantDynamic]s) remap through `mapValue`.
 */
class InvokeDynamicRemapTest {

    private val lambdaBsm = Handle(
        Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
        false,
    )

    /** iface `pkg/Iface.apply()V` → obf `a/a.a()V`; caller `pkg/Caller` → `b/b` */
    private fun tree() = treeOf().apply {
        cls("a/a", "pkg/Iface").method("a;()V", "apply;()V")
        cls("b/b", "pkg/Caller")
        cls("h/h", "pkg/Helper").method("c;()Ljava/lang/String;", "constant;()Ljava/lang/String;")
    }

    private fun ifaceJar() = classBytes("pkg/Iface", Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) {
        methods.add(org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "apply", "()V", null, null))
    }

    @Test
    fun lambdaCallsiteNameFollowsSamMapping() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")

        val caller = classBytes("pkg/Caller") {
            methods.add(org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "make", "()Lpkg/Iface;", null, null).apply {
                visitCode()
                instructions.add(InvokeDynamicInsnNode(
                    "apply", "()Lpkg/Iface;", lambdaBsm,
                    Type.getType("()V"),
                    Handle(Opcodes.H_INVOKESTATIC, "pkg/Caller", "lambda\$make\$0", "()V", false),
                    Type.getType("()V"),
                ))
                visitInsn(Opcodes.ARETURN)
                visitMaxs(1, 1)
                visitEnd()
            })
        }
        writeJar(input, mapOf("pkg/Iface.class" to ifaceJar(), "pkg/Caller.class" to caller))
        AsmRemapper.remap(input, out, tree(), named, obf, options = Options())

        val indy = readClasses(out).getValue("b/b").methods.first { it.name == "make" }
            .instructions.toArray().filterIsInstance<InvokeDynamicInsnNode>().single()
        assertEquals("a", indy.name, "lambda callsite name must follow the SAM method's mapping")
        assertEquals("()La/a;", indy.desc, "callsite desc (iface return type) must remap")
        val impl = indy.bsmArgs[1]
        assertIs<Handle>(impl)
        assertEquals("b/b", impl.owner, "impl-method handle owner remaps")
        assertEquals("lambda\$make\$0", impl.name, "unmapped synthetic impl name stays")
    }

    @Test
    fun constantDynamicArgsRemapRecursively() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")

        // condy whose bsm handle references Helper.constant — nested handles
        // must remap; the condy name itself is arbitrary and stays
        val condy = ConstantDynamic(
            "arbitrary", "Ljava/lang/String;",
            Handle(Opcodes.H_INVOKESTATIC, "pkg/Helper", "constant", "()Ljava/lang/String;", false),
        )
        val caller = classBytes("pkg/Caller") {
            methods.add(org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "make", "()Lpkg/Iface;", null, null).apply {
                visitCode()
                instructions.add(InvokeDynamicInsnNode(
                    "apply", "()Lpkg/Iface;", lambdaBsm,
                    Type.getType("()V"),
                    Handle(Opcodes.H_INVOKESTATIC, "pkg/Caller", "lambda\$make\$0", "()V", false),
                    Type.getType("()V"),
                    condy, // extra bsm arg
                ))
                visitInsn(Opcodes.ARETURN)
                visitMaxs(1, 1)
                visitEnd()
            })
            methods.add(org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "useCondy", "()Ljava/lang/String;", null, null).apply {
                visitCode()
                instructions.add(LdcInsnNode(condy))
                visitInsn(Opcodes.ARETURN)
                visitMaxs(1, 1)
                visitEnd()
            })
        }
        writeJar(input, mapOf("pkg/Iface.class" to ifaceJar(), "pkg/Caller.class" to caller,
            "pkg/Helper.class" to classBytes("pkg/Helper")))
        AsmRemapper.remap(input, out, tree(), named, obf, options = Options())

        val clsOut = readClasses(out).getValue("b/b")
        val indy = clsOut.methods.first { it.name == "make" }
            .instructions.toArray().filterIsInstance<InvokeDynamicInsnNode>().single()
        val remappedCondy = indy.bsmArgs[3]
        assertIs<ConstantDynamic>(remappedCondy)
        assertEquals("arbitrary", remappedCondy.name, "condy name is arbitrary — stays")
        assertEquals("h/h", remappedCondy.bootstrapMethod.owner)
        assertEquals("c", remappedCondy.bootstrapMethod.name, "condy bsm method name follows mapping")

        val ldc = clsOut.methods.first { it.name == "useCondy" }
            .instructions.toArray().filterIsInstance<LdcInsnNode>().single()
        val ldcCondy = ldc.cst
        assertIs<ConstantDynamic>(ldcCondy)
        assertEquals("h/h", ldcCondy.bootstrapMethod.owner)
        assertEquals("c", ldcCondy.bootstrapMethod.name)
    }

    @Test
    fun knownBsmCallsiteNamePassesThrough() {
        val dir = createTempDirectory()
        val input = dir.resolve("in.jar"); val out = dir.resolve("out.jar")

        // StringConcatFactory callsite — name is fixed JDK-side, not a member ref
        val caller = classBytes("pkg/Caller") {
            methods.add(org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "str", "()Ljava/lang/String;", null, null).apply {
                visitCode()
                instructions.add(InvokeDynamicInsnNode(
                    "makeConcatWithConstants", "(I)Ljava/lang/String;",
                    Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                        "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false),
                    "x=",
                ))
                visitInsn(Opcodes.ARETURN)
                visitMaxs(1, 2)
                visitEnd()
            })
        }
        writeJar(input, mapOf("pkg/Caller.class" to caller))
        AsmRemapper.remap(input, out, tree(), named, obf, options = Options())

        val indy = readClasses(out).getValue("b/b").methods.first { it.name == "str" }
            .instructions.toArray().filterIsInstance<InvokeDynamicInsnNode>().single()
        assertEquals("makeConcatWithConstants", indy.name)
        assertTrue(indy.bsm.owner == "java/lang/invoke/StringConcatFactory")
    }

}
