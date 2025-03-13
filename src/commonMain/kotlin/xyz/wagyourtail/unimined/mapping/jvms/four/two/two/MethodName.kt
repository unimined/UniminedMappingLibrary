package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

@JvmInline
value class MethodName private constructor(val value: UnqualifiedName) : Type {

    companion object: TypeCompanion<MethodName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return UnqualifiedName.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val value = UnqualifiedName.read(reader)
            if (value.value.contains('<') || value.value.contains('>')) {
                if (value.value != "<init>" && value.value != "<clinit>") {
                    throw IllegalArgumentException("Invalid method name, cannot contain < or >")
                }
            }
            append(value)
        }

        override fun unchecked(value: String) = MethodName(UnqualifiedName.unchecked(value))

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}