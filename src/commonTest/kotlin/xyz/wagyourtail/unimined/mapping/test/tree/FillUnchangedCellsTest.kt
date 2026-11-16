package xyz.wagyourtail.unimined.mapping.test.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.fillUnchangedCells
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [fillUnchangedCells] — loom `MappingNsCompleter` "un-changed" parity:
 * rows whose present names all agree gain the missing unqualified cells;
 * member descs remap through the tree remapper; env columns and
 * differing-name rows are never filled.
 */
class FillUnchangedCellsTest {

    private val official = Namespace("official")
    private val intermediary = Namespace("intermediary")
    private val named = Namespace("named")

    @Test
    fun `equal present names fill the missing column`() {
        val tree = mappingTree("official", "intermediary", "named") {
            clazz(null, "a/A", "a/A") {
                // present names agree — the row is un-obfuscated
                method(null, "m;()V", "m")
                field(null, "f;I", "f")
            }
        }
        tree.fillUnchangedCells()
        val cls = tree.getClass(intermediary, InternalName.unchecked("a/A"))!!
        assertEquals("a/A", cls.names[official]?.value)
        val m = cls.methods.single()
        assertEquals("m", m.names[official]?.value)
        assertEquals("()V", m.descs[official]?.value)
        val f = cls.fields.single()
        assertEquals("f", f.names[official]?.value)
        // FieldDescriptor.value is a FieldType — unwrap to the raw string
        assertEquals("I", f.descs[official]?.value?.value)
    }

    @Test
    fun `filled member descs remap through the tree`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B")
            clazz("a/C", null) {
                // name-only in official — `named` gains the same name
                // with the desc remapped through the tree
                field("f;La/A;", null)
            }
        }
        tree.fillUnchangedCells()
        val f = tree.getClass(official, InternalName.unchecked("a/C"))!!.fields.single()
        assertEquals("Lb/B;", f.descs[named]?.value?.value)
        assertEquals("f", f.names[named]?.value)
    }

    @Test
    fun `rows with differing names stay sparse`() {
        val tree = mappingTree("official", "intermediary", "named") {
            clazz("a/A", "i/A", null) {
                method("m;()V", "m_1")
            }
        }
        tree.fillUnchangedCells()
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        // class cells differ — `named` can't be guessed
        assertNull(cls.names[named])
        assertNull(cls.methods.single().names[named])
    }

    @Test
    fun `env columns are never filled`() {
        val tree = mappingTree("official", "named", "official:server") {
            clazz("a/A", "b/B", null) {
                method("m;()V", "n")
            }
        }
        tree.fillUnchangedCells()
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        // `official:server` marks side presence — an empty cell means
        // "absent on that side", not "same name"
        assertNull(cls.names[Namespace("official:server")])
        assertNull(cls.methods.single().names[Namespace("official:server")])
    }

    @Test
    fun `env-only rows do not fill`() {
        val tree = mappingTree("official", "named", "official:server") {
            clazz(null, null, "a/S") {
                method(null, null, "m;()V")
            }
        }
        tree.fillUnchangedCells()
        val cls = tree.getClass(Namespace("official:server"), InternalName.unchecked("a/S"))!!
        assertNull(cls.names[official])
        assertNull(cls.names[named])
        assertNull(cls.methods.single().names[official])
    }

    @Test
    fun `params locals and wildcard params fill`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                method("m;(I)V", "m") {
                    // named-only cells — the fill proves official got copied
                    parameter(0, null, null, "arg")
                    localVariable(0, null, null, "v")
                }
                cls.wildcard(
                    xyz.wagyourtail.unimined.mapping.element.WildcardType.METHOD,
                    mapOf(named to xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor.unchecked("()V"))
                ).parameter(
                    0, null,
                    mapOf(named to xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked("wp"))
                )
            }
        }
        tree.fillUnchangedCells()
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        val m = cls.methods.single()
        assertEquals("arg", m.parameters.single().names[official]?.value)
        assertEquals("v", m.localVariables.single().names[official]?.value)
        val wp = cls.wildcards.single().parameters.single()
        assertEquals("wp", wp.names[official]?.value)
    }

    @Test
    fun `packages fill`() {
        val tree = mappingTree("official", "named") {
            // present name is the same — the missing cell copies it
            pkg(null, "a/")
        }
        tree.fillUnchangedCells()
        val pkg = tree.packages.single()
        assertEquals("a/", pkg.names[official]?.value)
        assertEquals("a/", pkg.names[named]?.value)
    }

}
