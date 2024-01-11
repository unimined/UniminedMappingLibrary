package xyz.wagyourtail.unimined.mapping.jvms.signature.method

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.descriptor.method.VoidDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.signature.JavaTypeSignature
import kotlin.jvm.JvmInline

/**
 * Result:
 *   [JavaTypeSignature]
 *   [VoidDescriptor]
 */
@JvmInline
value class Result private constructor(val value: String) {

    companion object: TypeCompanion<Result> {
        val innerTypes = setOf(JavaTypeSignature, VoidDescriptor)

        override fun shouldRead(reader: BufferedSource) =
            innerTypes.firstOrNull { it.shouldRead(reader.peek()) }?.shouldRead(reader) == true

        override fun read(reader: BufferedSource) =
            Result(innerTypes.first { it.shouldRead(reader.peek()) }.read(reader).toString())

    }

    fun isJavaTypeSignature() = value[0] == 'L'

    fun isVoidDescriptor() = value[0] == 'V'

    fun getJavaTypeSignature() = JavaTypeSignature.read(value)

    fun getVoidDescriptor() = VoidDescriptor.read(value)

    override fun toString() = value

}