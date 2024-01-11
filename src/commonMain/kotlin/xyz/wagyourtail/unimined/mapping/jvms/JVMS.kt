package xyz.wagyourtail.unimined.mapping.util.jvms

import xyz.wagyourtail.unimined.mapping.util.jvms.descriptor.field.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.util.jvms.descriptor.method.MethodDescriptor

object JVMS {

    val unqualifiedNameIllagalChars = setOf('.', ';', '[', '/')


    /**
     * 4.3.2
     */
    fun parseFieldDescriptor(value: String) = FieldDescriptor.read(value)

    /**
     * 4.3.3
     */
    fun parseMethodDescriptor(value: String) = MethodDescriptor.read(value)

}