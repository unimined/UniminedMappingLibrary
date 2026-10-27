package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.metadata.AnnotationElement
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.ExceptionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.element.metadata.SignatureElement
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

    @Test
    fun `metadata sugar attaches to scoped and leaf rows`() {
        val tree = mappingTree("official", "named") {
            pkg("a/", "b/") {
                javadoc("package doc", "official")
            }
            clazz("a/A", "b/B") {
                javadoc("class doc", "named")
                signature("Lxyz/A<TE;>;", "named")
                annotation("Lcom/foo/Marker;", "named")
                interface_("com/foo/Bar", "named")
                enumExtension("EXTRA_CONST", "named")
                field("f;I", "g") {
                    annotation("Lcom/foo/FieldAnn;", "named")
                }
                method("m;()V", "n") {
                    exception("java/lang/IoError", "named")
                    parameter(0, null, "arg") {
                        javadoc("param doc", "named")
                    }
                }
            }
        }

        val pkg = tree.packages.single()
        assertEquals(
            "package doc",
            pkg.metadata.filterIsInstance<JavadocElement>().single().value.toString()
        )
        val cls = tree.getClass(off, InternalName.unchecked("a/A"))!!
        assertEquals(
            "class doc",
            cls.metadata.filterIsInstance<JavadocElement>().single().value.toString()
        )
        assertEquals(
            named,
            cls.metadata.filterIsInstance<SignatureElement>().single().baseNs
        )
        assertEquals(
            "Lcom/foo/Marker;",
            cls.metadata.filterIsInstance<AnnotationElement>().single().annotation.toString()
        )
        assertEquals(
            1, cls.metadata.filterIsInstance<InterfaceElement>().count()
        )
        assertEquals(
            "EXTRA_CONST",
            cls.metadata.filterIsInstance<EnumExtensionElement>()
                .single().name.toString()
        )
        assertEquals(
            1, cls.fields.single().metadata
                .filterIsInstance<AnnotationElement>().count()
        )
        val method = cls.methods.single()
        assertEquals(
            "java/lang/IoError",
            method.metadata.filterIsInstance<ExceptionElement>()
                .single().exception.toString()
        )
        assertEquals(
            "param doc",
            method.parameters.single().metadata
                .filterIsInstance<JavadocElement>().single().value.toString()
        )
    }

    @Test
    fun `constant group scope covers constants targets expressions`() {
        val tree = mappingTree("official", "named") {
            constantGroup(InlineType.PLAIN, "grp", "official") {
                constant("a/A", "CONST", "I")
                target("a/A.field", 1)
                expression("CONST", "1")
            }
        }

        val group = tree.constantGroups.single()
        assertEquals("grp", group.name)
        assertEquals(1, group.constants.toList().size)
        assertEquals(1, group.targets.toList().size)
        assertEquals(1, group.expressions.toList().size)
    }

}
