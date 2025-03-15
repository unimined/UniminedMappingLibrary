package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Identifier
import kotlin.jvm.JvmInline

/**
 * TypeVariableSignature:
 *   T [Identifier] ;
 */
@JvmInline
value class TypeVariableSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<TypeVariableSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'T'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('T'))
            append(Identifier.read(reader))
            append(reader.expect(';'))
        }

        override fun unchecked(value: String) = TypeVariableSignature(value)
    }

    fun getParts() = Identifier.unchecked(value.substring(1, value.length - 1))

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("T")
            getParts().accept(visitor)
            visitor(";")
        }
    }

    override fun toString() = value

}