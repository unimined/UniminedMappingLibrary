package xyz.wagyourtail.unimined.mapping.test.jvms.descriptor.method

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MethodDescriptorTests {

    @Test
    fun basics() {
        // no args
        JVMS.parseMethodDescriptor("()V")

        // single arg
        JVMS.parseMethodDescriptor("(Ljava/lang/String;)V")
        JVMS.parseMethodDescriptor("(Ljava/lang/String;I)V")

        // array return, multiple args
        JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[I")
        JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[[I")
        JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[[[I")

        // base arg
        JVMS.parseMethodDescriptor("(I)V")

        // array arg
        JVMS.parseMethodDescriptor("([S)V")
        JVMS.parseMethodDescriptor("([[S)V")
        JVMS.parseMethodDescriptor("(Z)V")
    }

    @Test
    fun check_methods_on_method() {
        assertEquals("()V", JVMS.parseMethodDescriptor("()V").toString())

        val (returnVal, args) = JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[[[I").getParts()

        // check return type
        assertEquals("[[[I", returnVal.toString())
        assertEquals("[[[I", returnVal.toString())
        assertEquals("Ljava/lang/String;", args[0].toString())
        assertEquals("I", args[1].toString())

        val (returnVal2, args2) = JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[[[I").getParts()

        assertEquals(args, args2)
        assertEquals(returnVal, returnVal2)
    }

    @Test
    fun check_create_function() {
        val desc = JVMS.createMethodDescriptor("V", "I", "Ljava/lang/String;")
        assertEquals("(ILjava/lang/String;)V", desc.toString())
        val (returnVal, args) = desc.getParts()
        assertEquals("V", returnVal.toString())
        assertEquals("I", args[0].toString())
        assertEquals("Ljava/lang/String;", args[1].toString())
    }

    @Test
    fun check_visitor() {
        val methodDesc = JVMS.parseMethodDescriptor("(Ljava/lang/String;I)[[[I")
        assertEquals("(Ljava/lang/String;I)[[[I", buildString {
            methodDesc.accept { obj, isLeaf ->
                if (isLeaf) {
                    append(obj.toString())
                }
                true
            }
        })
    }

    @Test
    fun check_illegal() {
        assertFails {
            JVMS.parseMethodDescriptor("()VZ")
        }

        assertFails {
            JVMS.parseMethodDescriptor("(")
        }

        assertFails {
            JVMS.parseMethodDescriptor("(Ljava/lang")
        }
    }


}