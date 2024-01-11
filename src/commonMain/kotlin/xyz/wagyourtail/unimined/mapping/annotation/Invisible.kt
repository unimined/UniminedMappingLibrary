package xyz.wagyourtail.unimined.mapping.annotation

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * Invisible:
 *   .invisible
 */
@JvmInline
value class Invisible private constructor(val value: String) {

    companion object: TypeCompanion<Invisible> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            if (reader.exhausted()) {
                return false
            }
            return reader.readUtf8CodePoint().checkedToChar() == '.'
        }

        override fun read(reader: BufferedSource) = try {
            Invisible(buildString {
                for (i in ".invisible") {
                    val char = reader.readUtf8CodePoint().checkedToChar()
                    if (char != i) {
                        throw IllegalArgumentException("Invalid invisible, expected $i, found $char")
                    }
                }
                append(".invisible")
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid invisible", e)
        }

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        visitor(this, true)
    }

    override fun toString() = value

}