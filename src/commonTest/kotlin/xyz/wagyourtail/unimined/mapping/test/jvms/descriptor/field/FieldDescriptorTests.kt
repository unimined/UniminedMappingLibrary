package xyz.wagyourtail.unimined.mapping.test.jvms.descriptor.field

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.test.jvms.buildStringAcceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class FieldDescriptorTests {

    @Test
    fun basics() {
        // identifier
        JVMS.parseFieldDescriptor("Ljava/lang/String;")
        JVMS.parseFieldDescriptor("Laab;")
        // all base types
        JVMS.parseFieldDescriptor("Z")
        JVMS.parseFieldDescriptor("B")
        JVMS.parseFieldDescriptor("C")
        JVMS.parseFieldDescriptor("S")
        JVMS.parseFieldDescriptor("I")
        JVMS.parseFieldDescriptor("J")
        JVMS.parseFieldDescriptor("F")
        JVMS.parseFieldDescriptor("D")
        // arrays
        JVMS.parseFieldDescriptor("[I")
        JVMS.parseFieldDescriptor("[[I")
        JVMS.parseFieldDescriptor("[[[I")
        JVMS.parseFieldDescriptor("[[[[Ljava/lang/String;")
    }

    @Test
    fun check_methods_on_object() {
        // check object types
        val str = JVMS.parseFieldDescriptor("Ljava/lang/String;")
        assertEquals("Ljava/lang/String;", str.toString())
        assertEquals("java/lang/String", str.value.getObjectType().getInternalName().toString())
        assertEquals(str.value.isBaseType(), false)
        assertEquals(str.value.isObjectType(), true)
        assertEquals(str.value.isArrayType(), false)
    }

    @Test
    fun check_methods_on_base() {
        // check base types
        val int = JVMS.parseFieldDescriptor("I")
        assertEquals("I", int.toString())
        assertEquals(int.value.isBaseType(), true)
        assertEquals(int.value.isObjectType(), false)
        assertEquals(int.value.isArrayType(), false)
    }

    @Test
    fun check_methods_on_array() {
        // check array types
        val intArray = JVMS.parseFieldDescriptor("[Ljava/lang/String;")
        assertEquals("[Ljava/lang/String;", intArray.toString())
        assertEquals(intArray.value.isBaseType(), false)
        assertEquals(intArray.value.isObjectType(), false)
        assertEquals(intArray.value.isArrayType(), true)
        val parts = intArray.value.getArrayType().getParts()
        assertEquals(1, parts.first)
        assertEquals("Ljava/lang/String;", parts.second.toString())

        val int2DArray = JVMS.parseFieldDescriptor("[[I")
        assertEquals("[[I", int2DArray.toString())
        assertEquals(int2DArray.value.isBaseType(), false)
        assertEquals(int2DArray.value.isObjectType(), false)
        assertEquals(int2DArray.value.isArrayType(), true)
        val parts2 = int2DArray.value.getArrayType().getParts()
        assertEquals(2, parts2.first)
        assertEquals("I", parts2.second.toString())

        val (dims, type) = int2DArray.value.getArrayType().getParts()
        assertEquals(2, dims)
        assertEquals("I", type.toString())
        assertEquals(type.value.isBaseType(), true)
        assertEquals(type.value.isObjectType(), false)
        assertEquals(type.value.isArrayType(), false)
    }

    @Test
    fun check_visitor() {
        val str = JVMS.parseFieldDescriptor("Ljava/lang/String;")
        assertEquals("Ljava/lang/String;", buildStringAcceptor(str))

        val int = JVMS.parseFieldDescriptor("I")
        assertEquals("I", buildStringAcceptor(int))

        val intArray = JVMS.parseFieldDescriptor("[Ljava/lang/String;")
        assertEquals("[Ljava/lang/String;", buildStringAcceptor(intArray))

        val int2DArray = JVMS.parseFieldDescriptor("[[I")
        assertEquals("[[I", buildStringAcceptor(int2DArray))
    }

    @Test
    fun check_illegal() {
        // check illegal types & incomplete strings
        assertFails {
            JVMS.parseFieldDescriptor("L")
        }

        assertFails {
            JVMS.parseFieldDescriptor("V")
        }

        assertFails {
            JVMS.parseFieldDescriptor("[")
        }

        assertFails {
            JVMS.parseFieldDescriptor("[[")
        }

        assertFails {
            JVMS.parseFieldDescriptor("Lillegal.chars;")
        }
    }

}