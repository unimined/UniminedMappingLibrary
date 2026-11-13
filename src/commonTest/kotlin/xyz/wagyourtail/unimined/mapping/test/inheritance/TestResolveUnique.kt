package xyz.wagyourtail.unimined.mapping.test.inheritance

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestResolveUnique {

    val TREE = """
        umf_it 1 0
        intermediary
        grand
         public gm ()V
         public gf I
        mid grand
         public mm ()V
         public mm (I)V
         private pm ()V
         public om ()V
         public mf I
        left mid
         public dm ()V
         public om ()V
         public mf I
        right mid
         public dm ()V
         public om ()V
        diamond left right
         public own ()V
        solo left
        """.trimIndent()

    val MAPPINGS = """
        umf 1 3
        intermediary named
        """.trimIndent()

    fun withGroups(body: suspend MemberGroups.() -> Unit) = runTest {
        MemberGroups(CachedInheritanceTree(UMFReader.read(StringCharReader(MAPPINGS)), StringCharReader(TREE))).body()
    }

    private fun MemberGroups.cls(name: String) = inheritance.classes[InternalName.unchecked(name)]!!

    @Test
    fun testUniqueInheritedMethod() = withGroups {
        // solo -> left -> mid: mm is declared only on mid
        val m = resolveUniqueMethod(cls("solo"), UnqualifiedName.unchecked("mm"), MethodDescriptor.unchecked("()V"))
        assertEquals("mid", m?.cls?.name?.value)
        assertEquals("mm", m?.info?.name?.value)
    }

    @Test
    fun testAmbiguousOverloads() = withGroups {
        // mid declares mm()V and mm(I)V — a desc-less lookup is ambiguous
        assertNull(resolveUniqueMethod(cls("mid"), UnqualifiedName.unchecked("mm")))
    }

    @Test
    fun testDiamondSameComponent() = withGroups {
        // left.om and right.om both override mid.om — one component, so
        // the name is still unique; the nearest (super-first) match wins
        val m = resolveUniqueMethod(cls("diamond"), UnqualifiedName.unchecked("om"), MethodDescriptor.unchecked("()V"))
        assertEquals("left", m?.cls?.name?.value)
    }

    @Test
    fun testDiamondDistinctComponents() = withGroups {
        // left.dm and right.dm share no declaring ancestor — genuinely ambiguous
        assertNull(resolveUniqueMethod(cls("diamond"), UnqualifiedName.unchecked("dm"), MethodDescriptor.unchecked("()V")))
    }

    @Test
    fun testNonVirtualAndDeepAncestor() = withGroups {
        assertEquals("mid", resolveUniqueMethod(cls("mid"), UnqualifiedName.unchecked("pm"), MethodDescriptor.unchecked("()V"))?.cls?.name?.value)
        // pm climbs through member-less left/right to mid
        assertEquals("mid", resolveUniqueMethod(cls("diamond"), UnqualifiedName.unchecked("pm"), MethodDescriptor.unchecked("()V"))?.cls?.name?.value)
        assertEquals("grand", resolveUniqueMethod(cls("diamond"), UnqualifiedName.unchecked("gm"), MethodDescriptor.unchecked("()V"))?.cls?.name?.value)
        assertNull(resolveUniqueMethod(cls("diamond"), UnqualifiedName.unchecked("nope")))
    }

    @Test
    fun testUniqueField() = withGroups {
        assertEquals("mid", resolveUniqueField(cls("mid"), UnqualifiedName.unchecked("mf"))?.cls?.name?.value)
        assertEquals("grand", resolveUniqueField(cls("diamond"), UnqualifiedName.unchecked("gf"))?.cls?.name?.value)
    }

    @Test
    fun testAmbiguousFields() = withGroups {
        // left.mf shadows mid.mf on the left branch but right still
        // reaches mid.mf — two visible declarations, ambiguous
        assertNull(resolveUniqueField(cls("diamond"), UnqualifiedName.unchecked("mf")))
        // self + shadowed ancestor both count
        assertNull(resolveUniqueField(cls("left"), UnqualifiedName.unchecked("mf")))
        assertNull(resolveUniqueField(cls("diamond"), UnqualifiedName.unchecked("nope")))
    }

}
