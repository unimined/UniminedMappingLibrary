package xyz.wagyourtail.unimined.mapping.jvms.ext.constant

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * StringConstant:
 *   " string "
 */
@JvmInline
value class StringConstant private constructor(val value: String) : Type {

    companion object : TypeCompanion<StringConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '"'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append("\"${reader.takeString().escape(true)}\"")
        }

        override fun unchecked(value: String): StringConstant {
            return StringConstant(value)
        }

    }

    fun unescape() = value.substring(1, value.length - 1).translateEscapes()

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}