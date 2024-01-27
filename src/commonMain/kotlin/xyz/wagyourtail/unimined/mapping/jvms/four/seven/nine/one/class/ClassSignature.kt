package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * ClassSignature:
 *   [[TypeParameters]] [SuperclassSignature] {[SuperinterfaceSignature]}
 */
@JvmInline
value class ClassSignature private constructor(val value: String) {

    companion object: TypeCompanion<ClassSignature> {

        override fun shouldRead(reader: CharReader): Boolean {
            if (TypeParameters.shouldRead(reader.copy())) {
                return TypeParameters.shouldRead(reader)
            }
            return SuperclassSignature.shouldRead(reader)
        }

        override fun read(reader: CharReader): ClassSignature {
            try {
                return ClassSignature(buildString {
                    if (TypeParameters.shouldRead(reader.copy())) {
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

        fun create(typeParams: List<String>?, superClass: String, superInterfaces: List<String>) =
            ClassSignature(buildString {
                if (typeParams != null) {
                    append(TypeParameters.read("<${typeParams.joinToString("")}>"))
                }
                append(SuperclassSignature.read(superClass))
                superInterfaces.forEach { append(SuperinterfaceSignature.read(it)) }
            })

        override fun unchecked(value: String) = ClassSignature(value)
    }

    fun getParts(): Triple<TypeParameters?, SuperclassSignature, List<SuperinterfaceSignature>> {
        return CharReader(value).use { buf ->
            val typeParams = if (TypeParameters.shouldRead(buf.copy())) {
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

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            getParts().let {
                it.first?.accept(visitor)
                it.second.accept(visitor)
                it.third.forEach { it.accept(visitor) }
            }
        }
    }

    override fun toString() = value

}