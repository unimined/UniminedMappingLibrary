package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

@JvmInline
value class UnqualifiedName private constructor(val value: String) : Type {

    companion object: TypeCompanion<UnqualifiedName> {
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

    override fun toString() = value

}