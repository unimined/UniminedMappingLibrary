package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.BaseType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * JavaTypeSignature:
 *   [ReferenceTypeSignature]
 *   [BaseType]
 */
@JvmInline
value class JavaTypeSignature private constructor(val type: String) : Type {

    companion object: TypeCompanion<JavaTypeSignature> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(ReferenceTypeSignature, BaseType)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String) = JavaTypeSignature(value)
    }

    fun isBaseType() = type.length == 1

    fun isReferenceTypeSignature() = !isBaseType()

    fun getBaseType() = BaseType.unchecked(type)

    fun getReferenceTypeSignature() = ReferenceTypeSignature.unchecked(type)

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isBaseType()) {
                getBaseType().accept(visitor)
            } else {
                getReferenceTypeSignature().accept(visitor)
            }
        }
    }

    override fun toString() = type

}