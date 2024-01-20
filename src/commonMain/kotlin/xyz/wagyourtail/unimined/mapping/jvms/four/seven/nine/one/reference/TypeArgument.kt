package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * TypeArgument:
 *   [[WildcardIndicator]] [ReferenceTypeSignature]
 *   *
 */
@JvmInline
value class TypeArgument private constructor(val value: String) {

    companion object: TypeCompanion<TypeArgument> {
        override fun shouldRead(reader: CharReader): Boolean {
            if (reader.peek() == '*') {
                reader.take()
                return true
            }
            if (WildcardIndicator.shouldRead(reader.copy())) {
                return WildcardIndicator.shouldRead(reader)
            }
            return ReferenceTypeSignature.shouldRead(reader)
        }

        override fun read(reader: CharReader): TypeArgument {
            if (!shouldRead(reader.copy())) {
                throw IllegalArgumentException("Invalid type argument")
            }
            if (reader.peek() == '*') {
                reader.take()
                return TypeArgument("*")
            }
            val wildcard = if (WildcardIndicator.shouldRead(reader.copy())) {
                WildcardIndicator.read(reader)
            } else {
                null
            }
            return TypeArgument(buildString {
                if (wildcard != null) {
                    append(wildcard)
                }
                append(ReferenceTypeSignature.read(reader))
            })
        }
    }

    fun isWildcard() = value == "*"

    fun getParts(): Pair<WildcardIndicator?, ReferenceTypeSignature>? {
        if (isWildcard()) {
            return null
        }
        CharReader(value).use {
            val wildcard = if (WildcardIndicator.shouldRead(it.copy())) {
                WildcardIndicator.read(it)
            } else {
                null
            }
            return Pair(wildcard, ReferenceTypeSignature.read(it))
        }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            if (isWildcard()) {
                visitor("*", true)
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