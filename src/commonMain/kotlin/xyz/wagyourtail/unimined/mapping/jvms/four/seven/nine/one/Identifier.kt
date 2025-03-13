package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

@JvmInline
value class Identifier private constructor(val value: String) : Type {
    companion object: TypeCompanion<Identifier> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val value = reader.takeUntil { it in JVMS.identifierIllegalChars }
            if (value.isEmpty()) {
                throw IllegalArgumentException("Invalid identifier name, cannot be empty")
            }
            append(value)
        }

        override fun unchecked(value: String) = Identifier(value)

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}