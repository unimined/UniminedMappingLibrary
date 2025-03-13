package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * TypeArgument:
 *   [[WildcardIndicator]] [ReferenceTypeSignature]
 *   *
 */
@JvmInline
value class TypeArgument private constructor(val value: String) : Type {

    companion object: TypeCompanion<TypeArgument> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (reader.peek() == '*') {
                reader.take()
                return true
            }
            if (WildcardIndicator.shouldRead(reader.copy())) {
                return WildcardIndicator.shouldRead(reader)
            }
            return ReferenceTypeSignature.shouldRead(reader)
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            if (reader.peek() == '*') {
                reader.take()
                append("*")
            } else {
                if (WildcardIndicator.shouldRead(reader.copy())) {
                    append(WildcardIndicator.read(reader))
                }
                append(ReferenceTypeSignature.read(reader))
            }
        }

        override fun unchecked(value: String) = TypeArgument(value)
    }

    fun isWildcard() = value == "*"

    fun getParts(): Pair<WildcardIndicator?, ReferenceTypeSignature>? {
        if (isWildcard()) {
            return null
        }
        StringCharReader(value).let {
            val wildcard = if (WildcardIndicator.shouldRead(it.copy())) {
                WildcardIndicator.read(it)
            } else {
                null
            }
            return Pair(wildcard, ReferenceTypeSignature.read(it))
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            if (isWildcard()) {
                visitor("*")
            } else {
                getParts()?.let {
                    it.first?.accept(visitor)
                    it.second.accept(visitor)
                }
            }
        }
    }

    override fun toString() = value

}