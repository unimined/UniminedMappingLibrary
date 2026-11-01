package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.envView
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvCollapseReproTest {

    private val official = Namespace("official")
    private val intermediary = Namespace("intermediary")

    private fun tree(memberNs: Namespace = official): MutableMappingTree {
        val tree = MutableMappingTree()
        val fd = tree.clazz(mapOf(official to InternalName.unchecked("fd")))
        // World's c-overloads — official `c` is name-ambiguous; distinct descs+ids
        fd.method(mapOf(
            memberNs to MethodNameAndDescriptor.unchecked("c;(IIII)Z"),
            intermediary to MethodNameAndDescriptor.unchecked("m_4155590;(IIII)Z"),
        ))
        fd.method(mapOf(
            memberNs to MethodNameAndDescriptor.unchecked("c;(Leq;)Z"),
            intermediary to MethodNameAndDescriptor.unchecked("m_7197503;(Leq;)Z"),
        ))
        fd.method(mapOf(
            memberNs to MethodNameAndDescriptor.unchecked("c;(F)F"),
            intermediary to MethodNameAndDescriptor.unchecked("m_2284587;(F)F"),
        ))
        return tree
    }

    private fun MutableMappingTree.fd() = classes.first { it.names[official]?.value == "fd" }

    /** desc → intermediary name for every `c` member */
    private fun MutableMappingTree.cNames(lookupNs: Namespace = official): Map<String, String> =
        fd().methods.filter { it.names[lookupNs]?.value == "c" }
            .associate { it.entries[lookupNs]!!.descriptor!!.value to it.names[intermediary]!!.value }

    @Test
    fun sameNameOverloadsStayDistinct() {
        val t = tree()
        assertEquals(3, t.fd().methods.toList().size)
        assertEquals(
            mapOf("(IIII)Z" to "m_4155590", "(Leq;)Z" to "m_7197503", "(F)F" to "m_2284587"),
            t.cNames(),
        )
    }

    @Test
    fun envViewKeepsOverloadsDistinct() {
        // members under official:client; envView(CLIENT) collapses to `official`
        val view = tree(official.forEnv("client")).envView("client")
        assertEquals(3, view.fd().methods.toList().size)
        assertEquals(
            mapOf("(IIII)Z" to "m_4155590", "(Leq;)Z" to "m_7197503", "(F)F" to "m_2284587"),
            view.cNames(),
        )
    }
}
