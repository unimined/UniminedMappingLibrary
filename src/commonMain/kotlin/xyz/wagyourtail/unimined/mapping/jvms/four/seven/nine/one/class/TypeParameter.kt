package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * TypeParameter:
 *   Identifier [ClassBound] {[InterfaceBound]}
 */
@JvmInline
value class TypeParameter private constructor(val value: String) {

    companion object: TypeCompanion<TypeParameter> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.identifierIllegalChars
        }

        override fun read(reader: CharReader<*>): TypeParameter {
            if (!shouldRead(reader.copy())) {
                throw IllegalArgumentException("Invalid type parameter")
            }
            return TypeParameter(buildString {
                append(reader.takeUntil { it in JVMS.identifierIllegalChars })
                append(ClassBound.read(reader))
                while (!reader.exhausted() && InterfaceBound.shouldRead(reader.copy())) {
                    append(InterfaceBound.read(reader))
                }
            })
        }

        override fun unchecked(value: String) = TypeParameter(value)
    }

    fun getParts(): Triple<String, ClassBound, List<InterfaceBound>> = StringCharReader(value).let {
        val name = it.takeUntil { it == ':' }
        val classBound = ClassBound.read(it)
        val interfaces = mutableListOf<InterfaceBound>()
        while (!it.exhausted()) {
            interfaces.add(InterfaceBound.read(it))
        }
        Triple(name, classBound, interfaces)
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