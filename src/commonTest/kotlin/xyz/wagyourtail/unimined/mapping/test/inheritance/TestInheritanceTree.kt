package xyz.wagyourtail.unimined.mapping.test.inheritance

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.propagator.CachedInheritanceTree
import xyz.wagyourtail.unimined.mapping.test.formats.tinyv2.TinyV2ReadWriteTest.Companion.mappings
import xyz.wagyourtail.unimined.mapping.test.formats.zip.getResource
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ParameterVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestInheritanceTree {

    val INHERITANCE_TREE = """
        umf_it 1 0
        intermediary
        clsA supA intfA intfB
         public fieldA I
         private fieldB I
         public|abstract methodA ()V
         private methodB ()V
         public methodD ()V
         public methodE ()V
        
        clsB clsA intfC
         public fieldA I
         private fieldB I
         public methodA ()V
         private methodB ()V
         public methodC ()V
         public methodD ()V
         
        clsC clsB
         public methodE ()V
        
        clsD clsB
         public|synthetic methodA ()V
         public methodE ()V
        
        intfA
         public|static fieldA I
         public methodC ()V
         
        intfB
         public methodC ()V
        
        intfC
         public methodD ()V
         private methodC ()V
        """.trimIndent()

    val UNPROP_MAPPINGS = """
        umf 1 0
        intermediary named
        c clsA Parent
         f fieldA;I field1
         f fieldB;I field2
         m methodA;()V _
         m methodB;()V cAmethod2
         m methodC;()V cAmethod5
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
         
        c clsB Child
         f fieldA;I field3
         f fieldB;I field4
         m methodA;()V cBmethod1
         m methodB;()V cBmethod2
         m methodC;()V iAmethod1
         m methodD;()V cBmethod4
        
        c clsD Child3
         m methodE;()V cAmethod4
        
        c intfA
         f fieldA;I field5
         m methodC;()V iAmethod1
         
        c intfB
         m methodC;()V _
         
        c intfC
         m methodD;()V iCmethod1
         m methodC;()V iCmethod2
        """.trimIndent()

    val PROP_MAPPINGS = """
        umf 1 0
        intermediary named
        c clsA Parent
         f fieldA;I field1
         f fieldB;I field2
         m methodA;()V cBmethod1
         m methodB;()V cAmethod2
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c clsB Child
         f fieldA;I field3
         f fieldB;I field4
         m methodA;()V cBmethod1
         m methodB;()V cBmethod2
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c clsC _
         m methodA;()V cBmethod1
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c clsD Child3
         m methodA;()V cBmethod1
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c intfA _
         f fieldA;I field5
         m methodC;()V iAmethod1
        c intfB _
         m methodC;()V iAmethod1
        c intfC _
         m methodC;()V iCmethod2
         m methodD;()V cAmethod3
        """.trimIndent()

    val FILTERED_MAPPINGS = """
        umf 1 0
        intermediary named
        c clsA Parent
         f fieldA;I field1
         f fieldB;I field2
         m methodA;()V cBmethod1
         m methodB;()V cAmethod2
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c clsB Child
         f fieldA;I field3
         f fieldB;I field4
         m methodA;()V cBmethod1
         m methodB;()V cBmethod2
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
        c clsC _
         m methodE;()V cAmethod4
        c clsD Child3
         m methodA;()V cBmethod1
         m methodE;()V cAmethod4
        c intfA _
         f fieldA;I field5
         m methodC;()V iAmethod1
        c intfB _
         m methodC;()V iAmethod1
        c intfC _
         m methodC;()V iCmethod2
         m methodD;()V cAmethod3
        """.trimIndent()

    @Test
    fun testInheritanceTree() = runTest {
        val mappings = Buffer().use { input ->
            input.writeUtf8(UNPROP_MAPPINGS)
            UMFReader.read(input)
        }

        val tree = CachedInheritanceTree(mappings, StringCharReader(INHERITANCE_TREE))
        tree.propagate(setOf(Namespace("named")))

        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, true), sort = true)
            output.readUtf8()
        }

        assertEquals(PROP_MAPPINGS.trimEnd().replace(" ", "\t"), output.trimEnd())
    }

    @Test
    fun testFilter() = runTest {
        val mappings = Buffer().use { input ->
            input.writeUtf8(PROP_MAPPINGS)
            UMFReader.read(input)
        }

        val filtered = MemoryMappingTree()
        val tree = CachedInheritanceTree(mappings, StringCharReader(INHERITANCE_TREE))
        tree.filtered(filtered)

        val output = Buffer().use { output ->
            filtered.accept(UMFWriter.write(output, true), sort = true)
            output.readUtf8()
        }
        assertEquals(FILTERED_MAPPINGS.trimEnd().replace(" ", "\t"), output.trimEnd())

    }

    val PREPROP_CHILD = """
umf	1	0
official	intermediary	yarn
c	tx	net/minecraft/class_2479	net/minecraft/nbt/NbtByteArray
c	tz	net/minecraft/class_2483	net/minecraft/nbt/AbstractNbtList
	m	c;(I)Lva;	method_10534	_
c	ue	net/minecraft/class_2495	net/minecraft/nbt/NbtIntArray
c	ug	net/minecraft/class_2499	net/minecraft/nbt/NbtList
	m	_	method_10534;(I)Lnet/minecraft/class_2520;	get
c	uh	net/minecraft/class_2501	net/minecraft/nbt/NbtLongArray
c	va	net/minecraft/class_2520	net/minecraft/nbt/NbtElement
    """.trimIndent()

    val CHILD_TREE = """
umf_it	1	0
official
tx	java/lang/Object	tz
	PUBLIC|SYNTHETIC	c	(I)Lva;
tz	java/lang/Object	java/lang/Iterable	va
	PUBLIC|ABSTRACT	c	(I)Lva;
ue	java/lang/Object	tz
	PUBLIC|SYNTHETIC	c	(I)Lva;
ug	java/util/AbstractList	tz
	PUBLIC	c	(I)Lva;
uh	java/lang/Object	tz
	PUBLIC|SYNTHETIC	c	(I)Lva;
    """.trimIndent()

    val POSTPROP_CHILD = """
umf	1	0
official	intermediary	yarn
c	tx	net/minecraft/class_2479	net/minecraft/nbt/NbtByteArray
	m	c;(I)Lva;	method_10534	get
c	tz	net/minecraft/class_2483	net/minecraft/nbt/AbstractNbtList
	m	c;(I)Lva;	method_10534	get
c	ue	net/minecraft/class_2495	net/minecraft/nbt/NbtIntArray
	m	c;(I)Lva;	method_10534	get
c	ug	net/minecraft/class_2499	net/minecraft/nbt/NbtList
	m	c;(I)Lva;	method_10534	get
c	uh	net/minecraft/class_2501	net/minecraft/nbt/NbtLongArray
	m	c;(I)Lva;	method_10534	get
c	va	net/minecraft/class_2520	net/minecraft/nbt/NbtElement
    """.trimIndent()

    @Test
    fun testChild() = runTest {
        val mappings = UMFReader.read(StringCharReader(PREPROP_CHILD))

        val tree = CachedInheritanceTree(mappings, StringCharReader(CHILD_TREE))

        for (m in mappings.namespaces.toSet() - tree.fns) {
            tree.propagate(setOf(m))
        }

        val outputText = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, true).delegator(object : Delegator() {
                override fun visitParameter(
                    delegate: InvokableVisitor<*>,
                    index: Int?,
                    lvOrd: Int?,
                    names: Map<Namespace, String>
                ): ParameterVisitor? {
                    return null
                }
            }), sort = true)
            output.readUtf8()
        }

        assertEquals(POSTPROP_CHILD.trimEnd(), outputText.trimEnd())
    }
}