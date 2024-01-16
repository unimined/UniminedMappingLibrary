package xyz.wagyourtail.unimined.mapping.test.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AnnotationTests {

    @Test
    fun basics() {
        Annotation.read("@Lcom/example/test;()")
    }

    @Test
    fun check_methods() {
        val annotation = Annotation.read("@Lcom/example/test;()")
        val (obj, elements, invis) = annotation.getParts()
        assertEquals("Lcom/example/test;", obj.toString())
        assertEquals(null, elements)
        assertEquals(null, invis)

        val annotation2 = Annotation.read("@Lcom/example/test;(a=1)")
        val (obj2, elements2, invis2) = annotation2.getParts()
        assertEquals("Lcom/example/test;", obj2.toString())
        assertEquals("a=1", elements2.toString())
        assertEquals(null, invis2)
        assertEquals("a=1", elements2!!.getParts()[0].toString())
        assertEquals("a", elements2.getParts()[0].getParts().first.unescape())
        assertEquals("1", elements2.getParts()[0].getParts().second.toString())

        val annotation3 = Annotation.read("@Lcom/example/test;(\"a\"=1,b=\"2\")")

        val (obj3, elements3, invis3) = annotation3.getParts()
        assertEquals("Lcom/example/test;", obj3.toString())
        assertEquals("\"a\"=1,b=\"2\"", elements3.toString())
        assertEquals(null, invis3)
        assertEquals("\"a\"=1", elements3!!.getParts()[0].toString())
        assertEquals("a", elements3.getParts()[0].getParts().first.unescape())
        assertEquals("1", elements3.getParts()[0].getParts().second.toString())
        assertEquals("b=\"2\"", elements3.getParts()[1].toString())
        assertEquals("b", elements3.getParts()[1].getParts().first.unescape())
        assertEquals("\"2\"", elements3.getParts()[1].getParts().second.toString())
        assertEquals("2", elements3.getParts()[1].getParts().second.getConstant().getString())

        val annotation4 = Annotation.read("@Lcom/example/test;(\"a\"=1,b=\"2\",c={1,2,3}).invisible")
        assertEquals("Lcom/example/test;", annotation4.getParts().first.toString())
        assertEquals("\"a\"=1,b=\"2\",c={1,2,3}", annotation4.getParts().second.toString())
        // check c
        assertEquals("c={1,2,3}", annotation4.getParts().second!!.getParts()[2].toString())
        assertEquals("c", annotation4.getParts().second!!.getParts()[2].getParts().first.unescape())
        assertEquals("{1,2,3}", annotation4.getParts().second!!.getParts()[2].getParts().second.toString())
        assertEquals("1", annotation4.getParts().second!!.getParts()[2].getParts().second.getArrayConstant().getParts()!!.getParts()[0].toString())
        assertEquals("2", annotation4.getParts().second!!.getParts()[2].getParts().second.getArrayConstant().getParts()!!.getParts()[1].toString())
        assertEquals("3", annotation4.getParts().second!!.getParts()[2].getParts().second.getArrayConstant().getParts()!!.getParts()[2].toString())
        // check invis
        assertEquals(".invisible", annotation4.getParts().third.toString())

        // empty array
        val annotation5 = Annotation.read("@Lcom/example/test;(\"a\"=1,b=\"2\",c={}).invisible")
        assertEquals("Lcom/example/test;", annotation5.getParts().first.toString())
        assertEquals("\"a\"=1,b=\"2\",c={}", annotation5.getParts().second.toString())
        // check c
        assertEquals("c={}", annotation5.getParts().second!!.getParts()[2].toString())
        assertEquals("c", annotation5.getParts().second!!.getParts()[2].getParts().first.unescape())
        assertEquals("{}", annotation5.getParts().second!!.getParts()[2].getParts().second.toString())
        assertEquals(null, annotation5.getParts().second!!.getParts()[2].getParts().second.getArrayConstant().getParts())

        // nested annotation
        val annotation6 = Annotation.read("@Lcom/example/test;(value=@Lcom/example/test2;(a=1))")
        assertEquals("Lcom/example/test;", annotation6.getParts().first.toString())
        assertEquals("value=@Lcom/example/test2;(a=1)", annotation6.getParts().second.toString())
        assertEquals("value", annotation6.getParts().second!!.getParts()[0].getParts().first.unescape())
        assertEquals("@Lcom/example/test2;(a=1)", annotation6.getParts().second!!.getParts()[0].getParts().second.toString())
        assertEquals("Lcom/example/test2;", annotation6.getParts().second!!.getParts()[0].getParts().second.getAnnotation().getParts().first.toString())
        assertEquals("a=1", annotation6.getParts().second!!.getParts()[0].getParts().second.getAnnotation().getParts().second.toString())
        assertEquals("a", annotation6.getParts().second!!.getParts()[0].getParts().second.getAnnotation().getParts().second!!.getParts()[0].getParts().first.unescape())
        assertEquals("1", annotation6.getParts().second!!.getParts()[0].getParts().second.getAnnotation().getParts().second!!.getParts()[0].getParts().second.toString())

        // enum constant
        val annotation7 = Annotation.read("@Lcom/example/test;(value=Lcom/example/test2;.enum)")
        assertEquals("Lcom/example/test;", annotation7.getParts().first.toString())
        assertEquals("value=Lcom/example/test2;.enum", annotation7.getParts().second.toString())
        assertEquals("value", annotation7.getParts().second!!.getParts()[0].getParts().first.unescape())
        assertEquals("Lcom/example/test2;.enum", annotation7.getParts().second!!.getParts()[0].getParts().second.toString())
        assertEquals("Lcom/example/test2;", annotation7.getParts().second!!.getParts()[0].getParts().second.getEnumConstant().getParts().first.toString())
        assertEquals("enum", annotation7.getParts().second!!.getParts()[0].getParts().second.getEnumConstant().getParts().second.toString())

        // class constant
        val annotation8 = Annotation.read("@Lcom/example/test;(value=Lcom/example/test2;)")
        assertEquals("Lcom/example/test;", annotation8.getParts().first.toString())
        assertEquals("value=Lcom/example/test2;", annotation8.getParts().second.toString())
        assertEquals("value", annotation8.getParts().second!!.getParts()[0].getParts().first.unescape())
        assertEquals("Lcom/example/test2;", annotation8.getParts().second!!.getParts()[0].getParts().second.toString())
    }

    @Test
    fun check_visitor() {
        val annotation = Annotation.read("@Lcom/example/test;()")
        assertEquals("@Lcom/example/test;()", buildString {
            annotation.accept { obj, leaf ->
                if (leaf) {
                    append(obj.toString())
                }
                true
            }
        })

        val annotation2 = Annotation.read("@Lcom/example/test;(a=1)")
        assertEquals("@Lcom/example/test;(a=1)", buildString {
            annotation2.accept { obj, leaf ->
                if (leaf) {
                    append(obj.toString())
                }
                true
            }
        })

        val annotation3 = Annotation.read("@Lcom/example/test;(a=1,b=\"2\")")
        assertEquals("@Lcom/example/test;(a=1,b=\"2\")", buildString {
            annotation3.accept { obj, leaf ->
                if (leaf) {
                    append(obj.toString())
                }
                true
            }
        })

        val annotation4 = Annotation.read("@Lcom/example/test;(\"a\"=1,b=\"2\",c={1,2,3}).invisible")
        assertEquals("@Lcom/example/test;(\"a\"=1,b=\"2\",c={1,2,3}).invisible", buildString {
            annotation4.accept { obj, leaf ->
                if (leaf) {
                    append(obj.toString())
                }
                true
            }
        })

    }

    @Test
    fun check_illegal() {

        assertFails {
            Annotation.read("@Lcom/example/test;")
        }

        assertFails {
            Annotation.read("@Lcom/example/test;(")
        }

        assertFails {
            Annotation.read("@Lcom/example/test;(value)")
        }

        assertFails {
            Annotation.read("@Lcom/example/test;(value=)")
        }

        assertFails {
            Annotation.read("@Lcom/example/test;(value=1c)")
        }

        assertFails {
            Annotation.read("@Lcom/example/test;(value=@Lcom/example/test;)")
        }

    }

}