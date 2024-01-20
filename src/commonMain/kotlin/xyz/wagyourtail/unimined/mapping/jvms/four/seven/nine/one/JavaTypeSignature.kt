package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.BaseType
import xyz.wagyourtail.unimined.mapping.util.CharReader
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

        override fun shouldRead(reader: CharReader) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader) =
            try {
                JavaTypeSignature(innerTypes.first {
                    it.shouldRead(
                        reader.copy()
                    )
                }.read(reader).toString())
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid java type signature", e)
            }
    }

    fun isBaseType() = type.length == 1

    fun isReferenceTypeSignature() = !isBaseType()

    fun getBaseType() = BaseType.read(type)

    fun getReferenceTypeSignature() = ReferenceTypeSignature.read(type)

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