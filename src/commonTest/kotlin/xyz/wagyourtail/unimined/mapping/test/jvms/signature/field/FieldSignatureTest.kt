package xyz.wagyourtail.unimined.mapping.test.jvms.signature.field

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FieldSignatureTest {

    @Test
    fun basics() {
        JVMS.parseFieldSignature("Ljava/lang/String;")
        // List<String>
        JVMS.parseFieldSignature("Ljava/lang/List<Ljava/lang/String;>;")
        // Map<String, String>
        JVMS.parseFieldSignature("Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>;")
        // T
        JVMS.parseFieldSignature("TT;")
        // String[][]
        JVMS.parseFieldSignature("[[Ljava/lang/String;")
        // Map<String, List<String>>
        JVMS.parseFieldSignature("Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/String;>;>;")
        // Map<T, List<[T>>
        JVMS.parseFieldSignature("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;")
    }

    @Test
    fun check_methods_on_field() {
        // Map<T, List<T[]>>
        val signature = JVMS.parseFieldSignature("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;")
        val (pkg, cls, suffix) = signature.value.getClassTypeSignature().getParts()
        assertEquals("java/util/", pkg.toString())
        assertEquals("Map<TT;Ljava/util/List<[TT;>;>", cls.toString())
        assertTrue(suffix.isEmpty())

        val (name, types) = cls.getParts()
        assertEquals("Map", name)
        assertEquals("<TT;Ljava/util/List<[TT;>;>", types.toString())
        assertEquals("TT;", types!!.getParts().first().toString())
        assertEquals("Ljava/util/List<[TT;>;", types.getParts()[1].toString())
    }

    @Test
    fun check_visitor() {
        val signature = JVMS.parseFieldSignature("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;")
        assertEquals("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;", buildString {
            signature.accept { part, isLeaf ->
                if (isLeaf) {
                    append(part.toString())
                }
                true
            }
        })

        val signature2 = JVMS.parseFieldSignature("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;")
        assertEquals("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;", buildString {
            signature2.accept { part, isLeaf ->
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
            JVMS.parseFieldSignature("Ljava/lang/String;Z")
        }

        assertFails {
            JVMS.parseFieldSignature("Ljava/lang/String")
        }

        assertFails {
            JVMS.parseFieldSignature("Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>;Z")
        }

        assertFails {
            JVMS.parseFieldSignature("Ljava/util/Map<Ljava/lang/String;?>;Z")
        }

        assertFails {
            JVMS.parseFieldSignature("TT<TT;>;")
        }

    }

}