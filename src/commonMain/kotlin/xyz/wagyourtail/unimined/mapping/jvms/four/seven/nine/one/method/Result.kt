package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.VoidDescriptor
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * Result:
 *   [JavaTypeSignature]
 *   [VoidDescriptor]
 */
@JvmInline
value class Result private constructor(val value: String) : Type {

    companion object: TypeCompanion<Result> {
        val innerTypes = setOf(JavaTypeSignature, VoidDescriptor)

        override fun shouldRead(reader: CharReader<*>) =
            innerTypes.firstOrNull { it.shouldRead(reader.copy()) }?.shouldRead(reader) == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(innerTypes.first { it.shouldRead(reader.copy()) }.read(reader))
        }

        override fun unchecked(value: String) = Result(value)
    }

    fun isJavaTypeSignature() = value[0] == 'L'

    fun isVoidDescriptor() = value[0] == 'V'

    fun getJavaTypeSignature() = JavaTypeSignature.unchecked(value)

    fun getVoidDescriptor() = VoidDescriptor.unchecked(value)

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isJavaTypeSignature()) {
                getJavaTypeSignature().accept(visitor)
            } else {
                getVoidDescriptor().accept(visitor)
            }
        }
    }

    override fun toString() = value

}