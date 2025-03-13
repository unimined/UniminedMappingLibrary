package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * Invisible:
 *   .invisible
 */
@JvmInline
value class Invisible private constructor(val value: String) : Type {

    companion object: TypeCompanion<Invisible> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (reader.exhausted()) {
                return false
            }
            return reader.take() == '.'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            for (i in ".invisible") {
                val char = reader.take()
                if (char != i) {
                    throw IllegalArgumentException("Invalid invisible, expected $i, found $char")
                }
            }
            append(".invisible")
        }

        override fun unchecked(value: String) = Invisible(value)

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}