package xyz.wagyourtail.unimined.mapping.test

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.tree.impl.fillInitNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [fillInitNames] completes `<init>` cells for namespaces missing them,
 * remapping the donor desc through the class's namespace remapper.
 */
class TestFillInitNames {

    private val named = Namespace("named")
    private val extra = Namespace("extra")

    private suspend fun tree(doc: String): MutableMappingTree = Buffer().use { input ->
        input.writeUtf8(doc)
        UMFReader.read(input) as MutableMappingTree
    }

    @Test
    fun fillsInitCellWithRemappedDesc() = runTest {
        val tree = tree("""
            umf	1	3
            official	named
            c	a/A	b/B
            	m	<init>;(La/A;)V	_
        """.trimIndent())

        tree.fillInitNames()

        val init = tree.classes.single().methods.single()
        assertEquals("<init>", init.names[named]?.value)
        // donor desc remapped through the class mapping, not copied
        assertEquals("(Lb/B;)V", init.descs[named]?.value)
    }

    @Test
    fun nonInitRowsStaySparse() = runTest {
        val tree = tree("""
            umf	1	3
            official	named	extra
            c	a/A	b/B	_
            	m	method_1;()V	_	_
        """.trimIndent())

        tree.fillInitNames()

        val method = tree.classes.single().methods.single()
        assertNull(method.names[named])
        assertNull(method.names[extra])
    }

    @Test
    fun unmappedClassRefsMapIdentity() = runTest {
        // a/X has no mapping — it keeps its source name in `named`,
        // the standard convention for unmapped classes
        val tree = tree("""
            umf	1	3
            official	named
            c	a/A	b/B
            	m	<init>;(La/X;)V	_
        """.trimIndent())

        tree.fillInitNames()

        val init = tree.classes.single().methods.single()
        assertEquals("<init>", init.names[named]?.value)
        assertEquals("(La/X;)V", init.descs[named]?.value)
    }

}
