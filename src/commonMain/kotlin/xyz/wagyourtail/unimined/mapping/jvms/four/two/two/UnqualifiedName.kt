package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import kotlin.jvm.JvmInline

@JvmInline
value class UnqualifiedName private constructor(val value: String) : Type {

    companion object: TypeCompanion<UnqualifiedName> {
        val init = UnqualifiedName("<init>")
        val clinit = UnqualifiedName("<clinit>")

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val value = reader.takeUntil { it in JVMS.unqualifiedNameIllegalChars }
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid unqualified name, cannot be empty")
            }
            append(value)
        }

        override fun unchecked(value: String) = UnqualifiedName(value)

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    fun withFieldDesc(desc: FieldDescriptor?): FieldNameAndDescriptor = FieldNameAndDescriptor.unchecked(buildString {
        append(this@UnqualifiedName)
        if (desc != null) {
            append(';')
            append(desc)
        }
    })

    fun withMethodDesc(desc: MethodDescriptor?): MethodNameAndDescriptor = MethodNameAndDescriptor.unchecked(buildString {
        append(this@UnqualifiedName)
        if (desc != null) {
            append(';')
            append(desc)
        }
    })

    override fun toString() = value

}