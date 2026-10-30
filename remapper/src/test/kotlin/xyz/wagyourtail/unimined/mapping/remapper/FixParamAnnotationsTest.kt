package xyz.wagyourtail.unimined.mapping.remapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.remapper.asm.JarFixups

/** Proguard-mangled ctor param-annotation fix (1.x FixParamAnnotations). */
class FixParamAnnotationsTest {

    private fun innerClass(): ClassNode {
        val cn = ClassNode()
        cn.access = Opcodes.ACC_PUBLIC
        cn.name = "pkg/Outer\$Inner"
        cn.superName = "java/lang/Object"
        cn.innerClasses.add(
            InnerClassNode("pkg/Outer\$Inner", "pkg/Outer", "Inner", Opcodes.ACC_PUBLIC)
        )
        // <init>(Lpkg/Outer;I)V — the this$0 outer ref IS in the
        // descriptor; proguard counts it again in the attr
        val init = MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Lpkg/Outer;I)V", null, null)
        init.visibleAnnotableParameterCount = 2
        @Suppress("UNCHECKED_CAST")
        init.visibleParameterAnnotations = arrayOfNulls<List<AnnotationNode>>(2).apply {
            this[0] = listOf(AnnotationNode("Lphantom;"))
            this[1] = listOf(AnnotationNode("Lreal;"))
        } as Array<List<AnnotationNode>>
        cn.methods.add(init)
        return cn
    }

    @Test
    fun `inner class ctor phantom annotation shifted out`() {
        val cn = innerClass()
        JarFixups.fixParamAnnotations(cn)
        val init = cn.methods.first()
        assertEquals(1, init.visibleAnnotableParameterCount)
        assertEquals("Lreal;", init.visibleParameterAnnotations[0][0].desc)
        assertNull(init.visibleParameterAnnotations[1] as Any?)
    }

    @Test
    fun `enum ctor synthetic pair accounted`() {
        val cn = innerClass()
        cn.access = cn.access or Opcodes.ACC_ENUM
        cn.innerClasses.clear()
        val init = cn.methods.first()
        init.desc = "(Ljava/lang/String;II)V" // synthetic pair + real int
        init.visibleAnnotableParameterCount = 3
        @Suppress("UNCHECKED_CAST")
        init.visibleParameterAnnotations = arrayOfNulls<List<AnnotationNode>>(3).apply {
            this[0] = listOf(AnnotationNode("Lp0;"))
            this[1] = listOf(AnnotationNode("Lp1;"))
            this[2] = listOf(AnnotationNode("Lreal;"))
        } as Array<List<AnnotationNode>>
        JarFixups.fixParamAnnotations(cn)
        assertEquals(1, init.visibleAnnotableParameterCount)
        assertEquals("Lreal;", init.visibleParameterAnnotations[0][0].desc)
    }
}

/** 1.x FixInnerClasses port — rebuilds InnerClasses attrs on
 *  Foo/Foo$Bar sibling sets. */
class FixInnerClassesTest {

    private fun cls(name: String) = ClassNode().apply {
        access = Opcodes.ACC_PUBLIC
        this.name = name
        superName = "java/lang/Object"
    }

    @Test
    fun `named inner classes get InnerClasses entries both sides`() {
        val foo = cls("pkg/Foo")
        val bar = cls("pkg/Foo\$Bar")
        val deep = cls("pkg/Foo\$Bar\$Baz")
        val anon = cls("pkg/Foo\$1")
        JarFixups.fixInnerClasses(listOf(
            "pkg/Foo.class" to foo, "pkg/Foo\$Bar.class" to bar,
            "pkg/Foo\$Bar\$Baz.class" to deep, "pkg/Foo\$1.class" to anon
        ))
        val fooEntries = foo.innerClasses.map { it.name to it.innerName }
        assertTrue(("pkg/Foo\$Bar" to "Bar") in fooEntries)
        assertTrue(("pkg/Foo\$Bar\$Baz" to "Baz") in fooEntries)
        assertTrue(foo.innerClasses.none { it.name == "pkg/Foo\$1" },
            "anonymous classes skipped")
        val barEntry = bar.innerClasses.first { it.name == "pkg/Foo\$Bar" }
        assertEquals("pkg/Foo", barEntry.outerName)
        assertTrue(barEntry.access and Opcodes.ACC_STATIC != 0)
        val deepEntry = deep.innerClasses.first { it.name == "pkg/Foo\$Bar\$Baz" }
        assertEquals("pkg/Foo\$Bar", deepEntry.outerName,
            "nearest named parent, not scan root")
    }
}
