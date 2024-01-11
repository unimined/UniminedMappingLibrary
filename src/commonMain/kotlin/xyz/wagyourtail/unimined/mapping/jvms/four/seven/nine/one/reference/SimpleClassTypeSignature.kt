package xyz.wagyourtail.unimined.mapping.jvms.signature.reference

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * SimpleClassTypeSignature:
 *   Identifier [[TypeArguments]]
 */
@JvmInline
value class SimpleClassTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<SimpleClassTypeSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: BufferedSource): SimpleClassTypeSignature {
            if (!shouldRead(reader.peek())) {
                throw IllegalArgumentException("Invalid simple class type signature")
            }
            try {
                return SimpleClassTypeSignature(buildString {
                    append(reader.takeUTF8Until { it.checkedToChar() in JVMS.identifierIllegalChars })
                    if (!reader.exhausted() && TypeArguments.shouldRead(reader.peek())) {
                        append(TypeArguments.read(reader))
                    }
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid simple class type signature", e)
            }
        }
    }

    fun getParts(): Pair<String, TypeArguments?> {
        return Pair(value.substringBefore('<'), if (value.contains("<")) TypeArguments.read("<${value.substringAfter('<')}") else null)
    }

    override fun toString() = value

}