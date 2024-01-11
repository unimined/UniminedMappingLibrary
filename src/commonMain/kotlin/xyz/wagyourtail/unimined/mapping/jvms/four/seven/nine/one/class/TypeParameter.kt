package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

/**
 * TypeParameter:
 *   Identifier [ClassBound] {[InterfaceBound]}
 */
@JvmInline
value class TypeParameter private constructor(val value: String) {

    companion object: TypeCompanion<TypeParameter> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: BufferedSource): TypeParameter {
            if (!shouldRead(reader.peek())) {
                throw IllegalArgumentException("Invalid type parameter")
            }
            return TypeParameter(buildString {
                append(reader.takeUTF8Until { it.checkedToChar() in JVMS.identifierIllegalChars })
                append(ClassBound.read(reader))
                while (!reader.exhausted() && InterfaceBound.shouldRead(reader.peek())) {
                    append(InterfaceBound.read(reader))
                }
            })
        }
    }

    fun getParts(): Triple<String, ClassBound, List<InterfaceBound>> {
        return Buffer().use { buf ->
            buf.writeUtf8(value)
            val name = buf.takeUTF8Until { it.checkedToChar() == ':' }
            val classBound = ClassBound.read(buf)
            val interfaces = mutableListOf<InterfaceBound>()
            while (!buf.exhausted()) {
                interfaces.add(InterfaceBound.read(buf))
            }
            Triple(name, classBound, interfaces)
        }
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            getParts().let { (name, classBound, interfaces) ->
                visitor(name, true)
                classBound.accept(visitor)
                interfaces.forEach { it.accept(visitor) }
            }
        }
    }

    override fun toString() = value

}