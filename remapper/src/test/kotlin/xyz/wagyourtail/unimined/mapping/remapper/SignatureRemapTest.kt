package xyz.wagyourtail.unimined.mapping.remapper

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import kotlin.test.Test
import kotlin.test.assertEquals

class SignatureRemapTest {
    private val from = Namespace("serverOfficial")
    private val to = Namespace("calamus")

    /** generic type variables must survive a signature remap verbatim —
     *  `T` is not a class name and must not be rewritten */
    @Test fun typeVarRoundTrip() {
        val tree = MutableMappingTree(setOf(from, to))
        val sig = JVMS.parseMethodSignature("(J)TV;")
        assertEquals("(J)TV;", sig.toString())
        val out = tree.map(from, to, sig)
        assertEquals("(J)TV;", out.toString())
    }
}
