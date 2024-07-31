package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.CharReader
import kotlin.jvm.JvmInline

/**
 * SimpleClassTypeSignature:
 *   Identifier [[TypeArguments]]
 */
@JvmInline
value class SimpleClassTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<SimpleClassTypeSignature> {

        override fun shouldRead(reader: CharReader): Boolean {
            return reader.take() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: CharReader): SimpleClassTypeSignature {
            if (!shouldRead(reader.copy())) {
                throw IllegalArgumentException("Invalid simple class type signature")
            }
            try {
                return SimpleClassTypeSignature(buildString {
                    append(reader.takeUntil { it in JVMS.identifierIllegalChars })
                    if (!reader.exhausted() && TypeArguments.shouldRead(reader.copy())) {
                        append(TypeArguments.read(reader))
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid simple class type signature", e)
            }
        }

        override fun unchecked(value: String) = SimpleClassTypeSignature(value)
    }

    fun getParts(): Pair<String, TypeArguments?> {
        return Pair(
            value.substringBefore('<'),
            if (value.contains("<")) TypeArguments.unchecked("<${value.substringAfter('<')}") else null
        )
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            getParts().let {
                visitor(it.first, true)
                it.second?.accept(visitor)
            }
        }
    }

    override fun toString() = value

}