package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
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
        override fun shouldRead(reader: BufferedSource): Boolean {
            if (reader.peek().readUtf8CodePoint().checkedToChar() == '*') {
                reader.readUtf8CodePoint()
                return true
            }
            if (WildcardIndicator.shouldRead(reader.peek())) {
                return WildcardIndicator.shouldRead(reader)
            }
            return ReferenceTypeSignature.shouldRead(reader)
        }

        override fun read(reader: BufferedSource): TypeArgument {
            if (!shouldRead(reader.peek())) {
                throw IllegalArgumentException("Invalid type argument")
            }
            if (reader.peek().readUtf8CodePoint().checkedToChar() == '*') {
                reader.readUtf8CodePoint()
                return TypeArgument("*")
            }
            val wildcard = if (WildcardIndicator.shouldRead(reader.peek())) {
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
        Buffer().use {
            it.writeUtf8(value)
            val wildcard = if (WildcardIndicator.shouldRead(it.peek())) {
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