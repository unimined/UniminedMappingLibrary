package xyz.wagyourtail.unimined.mapping.jvms

import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor

object JVMS {

    /**
     * 4.2.2
     */
    val unqualifiedNameIllagalChars = setOf('.', ';', '[', '/')

    /**
     * 4.7.9.1
     */

    val identifierIllegalChars = unqualifiedNameIllagalChars + setOf('<', '>', ':')

    /**
     * 4.2.2
     */
    fun checkUnqualifiedName(value: String) {
        if (value.isEmpty()) {
            throw IllegalArgumentException("Invalid unqualified name")
        }
        if (value.any { it in unqualifiedNameIllagalChars }) {
            throw IllegalArgumentException("Invalid unqualified name, found illegal character")
        }
    }

    /**
     * 4.2.2
     */
    fun checkMethodName(value: String) {
        if (value.isEmpty()) {
            throw IllegalArgumentException("Invalid method name")
        }
        if (value.any { it in unqualifiedNameIllagalChars }) {
            throw IllegalArgumentException("Invalid method name, found illegal character")
        }
        if (value.contains('<') || value.contains('>')) {
            if (value != "<init>" && value != "<clinit>") {
                throw IllegalArgumentException("Invalid method name, found illegal character")
            }
        }
    }

    /**
     * 4.3.2
     */
    fun parseFieldDescriptor(value: String) = FieldDescriptor.read(value)

    /**
     * 4.3.3
     */
    fun parseMethodDescriptor(value: String) = MethodDescriptor.read(value)
    fun createMethodDescriptor(returnType: String, vararg parameterTypes: String) =
        MethodDescriptor(returnType, *parameterTypes)

    /**
     * 4.7.9.1
     */
    fun parseClassSignature(value: String) = ClassSignature.read(value)
    fun createClassSignature(typeParams: List<String>?, superClass: String, superInterfaces: List<String>) =
        ClassSignature.create(typeParams, superClass, superInterfaces)

    /**
     * 4.7.9.1
     */
    fun parseMethodSignature(value: String) = MethodSignature.read(value)
    fun createMethodSignature(
        typeParams: List<String>?,
        params: List<String>,
        returnType: String,
        throws: List<String>
    ) = MethodSignature.create(typeParams, params, returnType, throws)

    /**
     * 4.7.9.1
     */
    fun parseFieldSignature(value: String) = FieldSignature.read(value)

}