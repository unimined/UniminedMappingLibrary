package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import kotlin.jvm.JvmInline

/**
 * ClassSignature:
 *   [[TypeParameters]] [SuperclassSignature] {[SuperInterfaceSignature]}
 */
@JvmInline
value class ClassSignature private constructor(val value: String) : Signature {

    companion object: TypeCompanion<ClassSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (TypeParameters.shouldRead(reader.copy())) {
                return TypeParameters.shouldRead(reader)
            }
            return SuperclassSignature.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            if (TypeParameters.shouldRead(reader.copy())) {
                append(TypeParameters.read(reader))
            }
            append(SuperclassSignature.read(reader))
            while (!reader.exhausted() && SuperInterfaceSignature.shouldRead(reader.copy())) {
                append(SuperInterfaceSignature.read(reader))
            }
        }

        fun create(typeParams: List<String>?, superClass: String, superInterfaces: List<String>) =
            ClassSignature(buildString {
                if (typeParams != null) {
                    append(TypeParameters.read("<${typeParams.joinToString("")}>"))
                }
                append(SuperclassSignature.read(superClass))
                superInterfaces.forEach { append(SuperInterfaceSignature.read(it)) }
            })

        override fun unchecked(value: String) = ClassSignature(value)
    }

    fun getParts(): Triple<TypeParameters?, SuperclassSignature, List<SuperInterfaceSignature>> = StringCharReader(value).let {
        val typeParams = if (TypeParameters.shouldRead(it.copy())) {
            TypeParameters.read(it)
        } else null
        val superclass = SuperclassSignature.read(it)
        val interfaces = mutableListOf<SuperInterfaceSignature>()
        while (!it.exhausted()) {
            interfaces.add(SuperInterfaceSignature.read(it))
        }
        Triple(typeParams, superclass, interfaces)
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            getParts().let {
                it.first?.accept(visitor)
                it.second.accept(visitor)
                it.third.forEach { it.accept(visitor) }
            }
        }
    }

    override fun toString() = value

}