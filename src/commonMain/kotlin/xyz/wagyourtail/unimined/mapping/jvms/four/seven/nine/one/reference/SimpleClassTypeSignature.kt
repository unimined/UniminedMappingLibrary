package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Identifier
import kotlin.jvm.JvmInline

/**
 * SimpleClassTypeSignature:
 *   [Identifier] [[TypeArguments]]
 */
@JvmInline
value class SimpleClassTypeSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<SimpleClassTypeSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: CharReader<*>): SimpleClassTypeSignature {
            if (!shouldRead(reader.copy())) {
                throw IllegalArgumentException("Invalid simple class type signature")
            }
            try {
                return SimpleClassTypeSignature(buildString {
                    append(Identifier.read(reader))
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

    fun getParts(): Pair<Identifier, TypeArguments?> {
        return Pair(
            Identifier.unchecked(value.substringBefore('<')),
            if (value.contains("<")) TypeArguments.unchecked("<${value.substringAfter('<')}") else null
        )
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            getParts().let {
                it.first.accept(visitor)
                it.second?.accept(visitor)
            }
        }
    }

    override fun toString() = value

}