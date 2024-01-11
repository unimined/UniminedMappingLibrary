package xyz.wagyourtail.unimined.mapping.jvms.signature.`class`

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * ClassSignature:
 *   [[TypeParameters]] [SuperclassSignature] {[SuperinterfaceSignature]}
 */
@JvmInline
value class ClassSignature private constructor(val value: String) {

    companion object: TypeCompanion<ClassSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            if (TypeParameters.shouldRead(reader.peek())) {
                return TypeParameters.shouldRead(reader)
            }
            return SuperclassSignature.shouldRead(reader)
        }

        override fun read(reader: BufferedSource): ClassSignature {
            try {
                return ClassSignature(buildString {
                    if (TypeParameters.shouldRead(reader.peek())) {
                        append(TypeParameters.read(reader))
                    }
                    append(SuperclassSignature.read(reader))
                    while (true) {
                        if (reader.exhausted()) {
                            break
                        }
                        append(SuperinterfaceSignature.read(reader))
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid class signature", e)
            }
        }

        fun create(typeParams: List<String>?, superClass: String, superInterfaces: List<String>) = ClassSignature(buildString {
            if (typeParams != null) {
                append(TypeParameters.read("<${typeParams.joinToString("")}>"))
            }
            append(SuperclassSignature.read(superClass))
            superInterfaces.forEach { append(SuperinterfaceSignature.read(it)) }
        })
    }

    fun getParts(): Triple<TypeParameters?, SuperclassSignature, List<SuperinterfaceSignature>> {
        return Buffer().use { buf ->
            buf.writeUtf8(value)
            val typeParams = if (TypeParameters.shouldRead(buf.peek())) {
                TypeParameters.read(buf)
            } else null
            val superclass = SuperclassSignature.read(buf)
            val interfaces = mutableListOf<SuperinterfaceSignature>()
            while (!buf.exhausted()) {
                interfaces.add(SuperinterfaceSignature.read(buf))
            }
            Triple(typeParams, superclass, interfaces)
        }
    }

    override fun toString() = value

}