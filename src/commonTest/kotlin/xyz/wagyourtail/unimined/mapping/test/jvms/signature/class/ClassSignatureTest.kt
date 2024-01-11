package xyz.wagyourtail.unimined.mapping.test.jvms.signature.`class`

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ClassSignatureTest {

    @Test
    fun basics() {
        JVMS.parseClassSignature("Ljava/lang/String;")
        // class ListTest<T extends String> extends ArrayList<T> implements Intf<Element>
        JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
    }

    @Test
    fun check_methods_on_class() {
        val signature = JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
        assertEquals("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;", signature.toString())
        val (typeParams, superClass, interfaces) = signature.getParts()
        assertEquals("<T:Ljava/lang/String;>", typeParams.toString())
        assertEquals("Ljava/util/ArrayList<TT;>;", superClass.toString())
        assertEquals("LIntf<LElement;>;", interfaces[0].toString())
        assertEquals("T:Ljava/lang/String;", typeParams!!.getParts()[0].toString())
        assertEquals("T", typeParams.getParts()[0].getParts().first)
        assertEquals("Ljava/lang/String;", typeParams.getParts()[0].getParts().second.getParts().toString())
        assertEquals("<TT;>", superClass.value.getParts().second.getParts().second.toString())
        assertEquals("TT;", superClass.value.getParts().second.getParts().second!!.getParts().first().toString())
    }

    @Test
    fun check_create_function() {
        val signature = JVMS.createClassSignature(listOf("T:Ljava/lang/String;"), "Ljava/util/ArrayList<TT;>;", listOf("LIntf<LElement;>;"))
        assertEquals("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;", signature.toString())
        val (typeParams, superClass, interfaces) = signature.getParts()
        assertEquals("<T:Ljava/lang/String;>", typeParams.toString())
        assertEquals("Ljava/util/ArrayList<TT;>;", superClass.toString())
        assertEquals("LIntf<LElement;>;", interfaces[0].toString())
        assertEquals("T:Ljava/lang/String;", typeParams!!.getParts()[0].toString())
        assertEquals("T", typeParams.getParts()[0].getParts().first)
        assertEquals("Ljava/lang/String;", typeParams.getParts()[0].getParts().second.getParts().toString())
        assertEquals("<TT;>", superClass.value.getParts().second.getParts().second.toString())
        assertEquals("TT;", superClass.value.getParts().second.getParts().second!!.getParts().first().toString())
    }

    @Test
    fun check_visitor() {
        val signature = JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
        assertEquals("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;", buildString {
            signature.accept { part, isLeaf ->
                if (isLeaf) {
                    append(part.toString())
                }
                true
            }
        })

        val signature2 = JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
        assertEquals("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;", buildString {
            signature2.accept { part, isLeaf ->
                if (isLeaf) {
                    append(part.toString())
                }
                true
            }
        })

        val signature3 = JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
        assertEquals("<T:Ljava/lang/String;>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;", buildString {
            signature3.accept { part, isLeaf ->
                if (isLeaf) {
                    append(part.toString())
                }
                true
            }
        })
    }

    @Test
    fun check_illegal() {

        assertFails {
            JVMS.parseClassSignature("Ljava/lang/String;Z")
        }

        assertFails {
            JVMS.parseClassSignature("Ljava/lang/String")
        }

        assertFails {
            JVMS.parseClassSignature("<T::>Ljava/util/ArrayList<TT;>;LIntf<LElement;>;")
        }

        assertFails {
            JVMS.parseClassSignature("<T:Ljava/lang/String;>Ljava/util/ArrayList<Tjava/lang/Test;>;LIntf<LElement;>")
        }

    }

}