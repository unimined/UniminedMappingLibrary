package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.BaseType
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * JavaTypeSignature:
 *   [ReferenceTypeSignature]
 *   [BaseType]
 */
@JvmInline
value class JavaTypeSignature private constructor(val type: String) {

    companion object: TypeCompanion<JavaTypeSignature> {

        val innerTypes: Set<TypeCompanion<*>> = setOf(ReferenceTypeSignature, BaseType)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>) =
            try {
                JavaTypeSignature(innerTypes.first {
                    it.shouldRead(
                        reader.copy()
                    )
                }.read(reader).toString())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid java type signature", e)
            }

        override fun unchecked(value: String) = JavaTypeSignature(value)
    }

    fun isBaseType() = type.length == 1

    fun isReferenceTypeSignature() = !isBaseType()

    fun getBaseType() = BaseType.unchecked(type)

    fun getReferenceTypeSignature() = ReferenceTypeSignature.unchecked(type)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isBaseType()) {
                getBaseType().accept(visitor)
            } else {
                getReferenceTypeSignature().accept(visitor)
            }
        }
    }

    override fun toString() = type

}