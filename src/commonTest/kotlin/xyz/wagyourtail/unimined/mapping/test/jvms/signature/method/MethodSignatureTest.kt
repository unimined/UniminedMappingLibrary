package xyz.wagyourtail.unimined.mapping.test.jvms.signature.method

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MethodSignatureTest {

    @Test
    fun basics() {
        JVMS.parseMethodSignature("(Ljava/lang/String;)V")
        // (List<String>)V
        JVMS.parseMethodSignature("(Ljava/util/List<Ljava/lang/String;>;)V")
        // (Map<String, ? extends String>)V
        JVMS.parseMethodSignature("(Ljava/util/Map<Ljava/lang/String;+Ljava/lang/String;>;)V")
        // (T)T
        JVMS.parseMethodSignature("(TT;)TT;")
        // (String[][])V
        JVMS.parseMethodSignature("([[Ljava/lang/String;)V")
        // (Map<String, List<String>>)V
        JVMS.parseMethodSignature("(Ljava/util/Map<Ljava/lang/String;Ljava/util/List<Ljava/lang/String;>;>;)V")
        // (Map<T, List<[T>>, List<?>)V
        JVMS.parseMethodSignature("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V")
        // (Map<T, List<T[]>>, List<?>)V throws Exception
        JVMS.parseMethodSignature("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;")
    }

    @Test
    fun check_methods_on_method() {
        // (Map<T, List<T[]>>, List<?>)V throws Exception
        val signature = JVMS.parseMethodSignature("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;")
        val (typeParams, rest) = signature.getParts()
        val (params, ret, throwable) = rest
        assertEquals("V", ret.toString())
        assertEquals("^Ljava/lang/Exception;", throwable.first().toString())
        assertEquals("Ljava/lang/Exception;", throwable.first().getClassTypeSignature().toString())
        assertEquals("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;", params.first().toString())
        assertEquals("Ljava/util/List<*>;", params[1].toString())
    }


    @Test
    fun check_create_function() {
        val signature = JVMS.createMethodSignature(listOf("T:Ljava/lang/String;"), listOf("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;", "Ljava/util/List<*>;"), "V", listOf("Ljava/lang/Exception;"))
        assertEquals("<T:Ljava/lang/String;>(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;", signature.toString())
        val (typeParams, rest) = signature.getParts()
        val (params, ret, throwable) = rest
        assertEquals("V", ret.toString())
        assertEquals("^Ljava/lang/Exception;", throwable.first().toString())
        assertEquals("Ljava/lang/Exception;", throwable.first().getClassTypeSignature().toString())
        assertEquals("Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;", params.first().toString())
        assertEquals("Ljava/util/List<*>;", params[1].toString())
    }

    @Test
    fun check_visitor() {
        val signature = JVMS.parseMethodSignature("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;")
        assertEquals("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;", buildString {
            signature.accept { part, isLeaf ->
                if (isLeaf) {
                    append(part.toString())
                }
                true
            }
        })

        val signature2 = JVMS.parseMethodSignature("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;")
        assertEquals("(Ljava/util/Map<TT;Ljava/util/List<[TT;>;>;Ljava/util/List<*>;)V^Ljava/lang/Exception;", buildString {
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
            JVMS.parseMethodSignature("(Ljava/lang/String)V")
        }

        assertFails {
            JVMS.parseMethodSignature("(Ljava/lang/String;Z)T")
        }

        assertFails {
            JVMS.parseMethodSignature("Ljava/lang/String;Z)V")
        }

        assertFails {
            JVMS.parseMethodSignature("(Ljava/util/Map<Ljava/lang/String;+Ljava/lang/String;>;)VZ")
        }

        assertFails {
            JVMS.parseMethodSignature("(Ljava/util/Map<Ljava/lang/String;+Ljava/lang/String;>;VZ")
        }

    }

}