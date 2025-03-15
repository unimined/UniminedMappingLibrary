package xyz.wagyourtail.unimined.mapping.test.inheritance

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.propagator.CachedInheritanceTree
import kotlin.test.Test
import kotlin.test.assertEquals

class TestInheritanceTree {

    val INHERITANCE_TREE = """
        umf_it 1 0
        intermediary
        clsA supA intfA intfB
         public fieldA I
         private fieldB I
         public methodA ()V
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

    val MAPPINGS = """
        umf 1 0
        intermediary named
        c clsA Parent
         f fieldA;I field1
         f fieldB;I field2
         m methodA;()V cAmethod1
         m methodB;()V cAmethod2
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

    val EXPECTED_MAPPINGS = """
        umf 1 0
        intermediary named
        c clsA Parent
         f fieldA;I field1
         f fieldB;I field2
         m methodA;()V cAmethod1
         m methodB;()V cAmethod2
         m methodD;()V cAmethod3
         m methodE;()V cAmethod4
        c clsB Child
         f fieldA;I field3
         f fieldB;I field4
         m methodA;()V cAmethod1
         m methodB;()V cBmethod2
         m methodC;()V iAmethod1
         m methodD;()V cAmethod3
        c clsD Child3
         m methodE;()V cAmethod4
        c intfA _
         f fieldA;I field5
         m methodC;()V iAmethod1
        c intfB _
         m methodC;()V iAmethod1
        c intfC _
         m methodD;()V cAmethod3
         m methodC;()V iCmethod2
        c clsC _
         m methodE;()V cAmethod4
        """.trimIndent()

    @Test
    fun testInheritanceTree() = runTest {
        val mappings = Buffer().use { input ->
            input.writeUtf8(MAPPINGS)
            UMFReader.read(input)
        }

        val tree = CachedInheritanceTree(mappings, StringCharReader(INHERITANCE_TREE))
        tree.propagate(setOf(Namespace("named")))

        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output, true))
            output.readUtf8()
        }

        assertEquals(EXPECTED_MAPPINGS.trimEnd().replace(" ", "\t"), output.trimEnd())
    }
}