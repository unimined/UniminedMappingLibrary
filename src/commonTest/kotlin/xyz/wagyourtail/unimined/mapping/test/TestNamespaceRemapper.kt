package xyz.wagyourtail.unimined.mapping.test

import kotlinx.coroutines.test.runTest
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.BaseType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestNamespaceRemapper {

    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")

    private suspend fun remapper() = UMFReader.read(
        """
            umf	1	2
            intermediary	named
            k	com/old/	com/new/
            c	eqm	net/minecraft/Example
            	f	field_1;I	mappedField;I
            	m	method_1;(Leqm;)V	mappedMethod;(Lnet/minecraft/Example;)V
        """.trimIndent()
    ).remapper(intermediary, named)

    @Test
    fun mapsClassesAndFallsBackToPackages() = runTest {
        val remapper = remapper()
        assertEquals(
            InternalName.unchecked("net/minecraft/Example"),
            remapper.mapClass(InternalName.unchecked("eqm"))
        )
        // unmapped class in a mapped package still remaps the package part
        assertEquals(
            InternalName.unchecked("com/new/Foo"),
            remapper.mapClass(InternalName.unchecked("com/old/Foo"))
        )
        // entirely unmapped names pass through
        assertEquals(
            InternalName.unchecked("unmapped/Cls"),
            remapper.mapClass(InternalName.unchecked("unmapped/Cls"))
        )
    }

    @Test
    fun mapsMembersByNameAndDesc() = runTest {
        val remapper = remapper()
        val owner = InternalName.unchecked("eqm")
        assertEquals(
            UnqualifiedName.unchecked("mappedField"),
            remapper.mapField(owner, UnqualifiedName.unchecked("field_1"), FieldDescriptor.unchecked("I"))
        )
        assertEquals(
            UnqualifiedName.unchecked("mappedMethod"),
            remapper.mapMethod(owner, UnqualifiedName.unchecked("method_1"), MethodDescriptor.unchecked("(Leqm;)V"))
        )
        // misses keep the original name
        assertEquals(
            UnqualifiedName.unchecked("nope"),
            remapper.mapMethod(owner, UnqualifiedName.unchecked("nope"), null)
        )
    }

    @Test
    fun mapsTypesGenerically() = runTest {
        val remapper = remapper()
        assertEquals(
            MethodDescriptor.unchecked("(Lnet/minecraft/Example;)V"),
            remapper.map(MethodDescriptor.unchecked("(Leqm;)V"))
        )
        // the typed signature path (what PR #6 wanted): ClassSignature in, ClassSignature out
        val mapped = remapper.map(ClassSignature.unchecked("Leqm;"))
        assertEquals(ClassSignature.unchecked("Lnet/minecraft/Example;"), mapped)
        // unmappable types throw rather than silently passing through
        assertFailsWith<IllegalArgumentException> {
            remapper.map(BaseType.unchecked("I"))
        }
    }

    @Test
    fun identityRemapperIsCheap() = runTest {
        val identity = UMFReader.read(
            """
                umf	1	2
                intermediary	named
                c	eqm	net/minecraft/Example
            """.trimIndent()
        ).remapper(intermediary, intermediary)
        assertEquals(
            InternalName.unchecked("eqm"),
            identity.mapClass(InternalName.unchecked("eqm"))
        )
    }

    @Test
    fun genericMapOnTreeMatchesRemapper() = runTest {
        val tree = UMFReader.read(
            """
                umf	1	2
                intermediary	named
                c	eqm	net/minecraft/Example
            """.trimIndent()
        )
        assertEquals(
            FieldDescriptor.unchecked("Lnet/minecraft/Example;"),
            tree.map(intermediary, named, FieldDescriptor.unchecked("Leqm;"))
        )
    }

}
