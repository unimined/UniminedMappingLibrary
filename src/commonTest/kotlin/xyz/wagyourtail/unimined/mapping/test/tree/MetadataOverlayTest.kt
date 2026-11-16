package xyz.wagyourtail.unimined.mapping.test.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.AnnotationElement
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.mappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.mergeMetadataOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [mergeMetadataOverlay] — javadoc/annotation layers matched by the
 * overlay's source namespace: first javadoc wins, annotations merge by
 * value, names are never touched, unmatched rows skip.
 */
class MetadataOverlayTest {

    private val official = Namespace("official")
    private val named = Namespace("named")

    private fun base() = mappingTree("official", "named") {
        clazz("a/A", "b/B") {
            method("m;()V", "n") {
                parameter(0, null, "o", "p")
            }
            field("f;I", "g")
        }
        clazz("a/C", "b/C")
    }

    @Test
    fun `javadoc and annotations merge onto matching rows`() {
        val tree = base()
        val overlay = mappingTree("named") {
            clazz("b/B") {
                javadoc("class doc", "named")
                annotation("Ltest/Marker;", "named")
                method("n") {
                    javadoc("method doc", "named")
                    parameter(0, null, "p") {
                        javadoc("param doc", "named")
                    }
                }
                field("g") {
                    javadoc("field doc", "named")
                }
            }
        }
        assertTrue(tree.mergeMetadataOverlay(overlay))

        val cls = tree.getClass(named, InternalName.unchecked("b/B"))!!
        assertEquals("class doc",
            cls.metadata.filterIsInstance<JavadocElement>().single().value.toString())
        assertEquals(1, cls.metadata.filterIsInstance<AnnotationElement>().size)

        val m = cls.methods.single()
        assertEquals("method doc",
            m.metadata.filterIsInstance<JavadocElement>().single().value.toString())
        assertEquals("param doc",
            m.parameters.single().metadata.filterIsInstance<JavadocElement>().single().value.toString())
        val f = cls.fields.single()
        assertEquals("field doc",
            f.metadata.filterIsInstance<JavadocElement>().single().value.toString())
    }

    @Test
    fun `first javadoc wins and annotations dedup by value`() {
        val tree = mappingTree("official", "named") {
            clazz("a/A", "b/B") {
                javadoc("original", "official")
            }
        }
        val overlay = mappingTree("named") {
            clazz("b/B") {
                javadoc("overlay doc", "named")
                annotation("Ltest/Dup;", "named")
            }
        }
        val overlay2 = mappingTree("named") {
            clazz("b/B") {
                javadoc("overlay2 doc", "named")
                annotation("Ltest/Dup;", "named")
                annotation("Ltest/Other;", "named")
            }
        }
        assertTrue(tree.mergeMetadataOverlay(overlay))
        assertTrue(tree.mergeMetadataOverlay(overlay2))

        val cls = tree.getClass(named, InternalName.unchecked("b/B"))!!
        val docs = cls.metadata.filterIsInstance<JavadocElement>()
        assertEquals(1, docs.size)
        assertEquals("original", docs.single().value.toString())
        val annotations = cls.metadata.filterIsInstance<AnnotationElement>()
            .map { it.annotation.toString() }
        // Ltest/Dup; landed once; the new one appended
        assertEquals(2, annotations.size)
        assertTrue("Ltest/Dup;" in annotations && "Ltest/Other;" in annotations)
    }

    @Test
    fun `unmatched overlay rows skip silently`() {
        val tree = base()
        val overlay = mappingTree("named") {
            // no b/B-mapped class in the base
            clazz("b/Z") {
                javadoc("nowhere", "named")
                method("zz") {
                    javadoc("also nowhere", "named")
                }
            }
            // member that doesn't exist on a matched class
            clazz("b/C") {
                method("ghost;()V") {
                    javadoc("no target", "named")
                }
            }
        }
        assertTrue(tree.mergeMetadataOverlay(overlay))
        val c = tree.getClass(named, InternalName.unchecked("b/C"))!!
        assertTrue(c.metadata.none())
        assertNull(tree.getClass(named, InternalName.unchecked("b/Z")))
    }

    @Test
    fun `overlay keyed by explicit srcNs`() {
        val tree = base()
        // overlay authored with two columns — match on `official` instead
        // of its first namespace
        val overlay = mappingTree("named", "official") {
            clazz("b/B", "a/A") {
                javadoc("official-keyed doc", "official")
            }
        }
        assertTrue(tree.mergeMetadataOverlay(overlay, official))
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        assertEquals("official-keyed doc",
            cls.metadata.filterIsInstance<JavadocElement>().single().value.toString())
    }

    @Test
    fun `srcNs absent from the tree returns false`() {
        val tree = base()
        val overlay = mappingTree("intermediary") {
            clazz("i/A") {
                javadoc("doc", "intermediary")
            }
        }
        // `intermediary` is not a declared column of the base tree
        assertFalse(tree.mergeMetadataOverlay(overlay))
        assertTrue(tree.getClass(named, InternalName.unchecked("b/B"))!!
            .metadata.none())
    }

    @Test
    fun `names are never modified`() {
        val tree = base()
        val overlay = mappingTree("named") {
            clazz("b/B") {
                // a conflicting class name in the overlay does not rename
                javadoc("doc", "named")
            }
        }
        tree.mergeMetadataOverlay(overlay)
        val cls = tree.getClass(official, InternalName.unchecked("a/A"))!!
        assertEquals("a/A", cls.names[official]?.value)
        assertEquals("b/B", cls.names[named]?.value)
    }

}
