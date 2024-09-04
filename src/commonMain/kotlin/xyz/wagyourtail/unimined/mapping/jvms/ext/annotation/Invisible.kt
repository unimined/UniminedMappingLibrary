package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * Invisible:
 *   .invisible
 */
@JvmInline
value class Invisible private constructor(val value: String) {

    companion object: TypeCompanion<Invisible> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (reader.exhausted()) {
                return false
            }
            return reader.take() == '.'
        }

        override fun read(reader: CharReader<*>) = try {
            Invisible(buildString {
                for (i in ".invisible") {
                    val char = reader.take()
                    if (char != i) {
                        throw IllegalArgumentException("Invalid invisible, expected $i, found $char")
                    }
                }
                append(".invisible")
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid invisible", e)
        }

        override fun unchecked(value: String) = Invisible(value)

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value

}