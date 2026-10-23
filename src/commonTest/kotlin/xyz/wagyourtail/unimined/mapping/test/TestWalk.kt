package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.JavadocElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestWalk {

    private val intermediary = Namespace("intermediary")

    private fun tree(): Pair<MutableMappingTree, Pair<ClassMapping, MethodMapping>> {
        val tree = MutableMappingTree()
        val clsA = tree.clazz(mapOf(intermediary to InternalName.unchecked("clsA")))
        clsA.addMetadata(JavadocElement("cls javadoc", intermediary))
        val method = clsA.method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("methodA;()V")))
        method.addMetadata(JavadocElement("method javadoc", intermediary))
        tree.clazz(mapOf(intermediary to InternalName.unchecked("clsB")))
        return tree to (clsA to method)
    }

    @Test
    fun walkMappingsCoversSubtreeDfs() {
        val (tree, _) = tree()
        val walked = tree.walkMappings().toList()
        // tree, clsA, methodA, clsB — self first, depth-first
        assertEquals(4, walked.size)
        assertSame(tree, walked[0])
        assertTrue(walked[1] is ClassMapping)
        assertTrue(walked[2] is MethodMapping)
        assertTrue(walked[3] is ClassMapping)
    }

    @Test
    fun walkMappingsFromNode() {
        val (_, cm) = tree()
        val (clsA, method) = cm
        val walked = clsA.walkMappings().toList()
        assertEquals(2, walked.size)
        assertSame(clsA, walked[0])
        assertSame(method, walked[1])
    }

    @Test
    fun walkMetadataCollectsAllMetadata() {
        val (tree, _) = tree()
        val meta = tree.walkMetadata().toList()
        assertEquals(2, meta.size)
        assertEquals("cls javadoc", (meta[0] as JavadocElement).value.toString())
        assertEquals("method javadoc", (meta[1] as JavadocElement).value.toString())
    }

    @Test
    fun walkMetadataPairsCarriesOwner() {
        val (tree, cm) = tree()
        val (clsA, method) = cm
        val pairs = tree.walkMetadataPairs().toList()
        assertEquals(2, pairs.size)
        assertSame(clsA, pairs[0].first)
        assertSame(method, pairs[1].first)
    }

}
