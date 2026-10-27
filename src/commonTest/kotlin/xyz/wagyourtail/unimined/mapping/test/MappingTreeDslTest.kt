package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.mappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappingTreeDslTest {

    private val off = Namespace("official")
    private val named = Namespace("named")
    private val extra = Namespace("extra")

    @Test
    fun `builds tree from positional cells`() {
        val tree = mappingTree("official", "named") {
            pkg("a/", "b/")
            clazz("a/A", "b/B") {
                field("f;I", "g")
                method("m;()V", "n") {
                    parameter(0, null, null, "arg")
                    localVariable(0, null, null, "v")
                }
            }
            clazz("a/C")
        }

        assertEquals(listOf(off, named), tree.namespaces.toList())
        assertEquals(
            "b/",
            tree.packages.first { it.names[off] == PackageName.unchecked("a/") }
                .names[named]?.value
        )
        val cls = tree.getClass(off, InternalName.unchecked("a/A"))!!
        assertEquals("b/B", cls.names[named]!!.value)
        assertEquals("g", cls.fields.single().names[named]!!.value)
        val method = cls.methods.single { it.names[off]!!.value == "m" }
        assertEquals("n", method.names[named]!!.value)
        assertEquals("()V", method.descs[off]!!.value)
        assertNull(method.descs[named])
        assertEquals("arg", method.parameters.single().names[named]!!.value)
        assertEquals("v", method.localVariables.single().names[named]!!.value)
        // src-only class has no dst cell
        assertNull(
            tree.getClass(off, InternalName.unchecked("a/C"))!!.names[named]
        )
    }

    @Test
    fun `null cells leave sparse namespaces`() {
        val tree = mappingTree("official", "named", "extra") {
            clazz("a/A", "b/B")
        }
        val cls = tree.classes.single()
        assertNull(cls.names[extra])
        assertEquals("b/B", cls.names[named]!!.value)
    }

}
