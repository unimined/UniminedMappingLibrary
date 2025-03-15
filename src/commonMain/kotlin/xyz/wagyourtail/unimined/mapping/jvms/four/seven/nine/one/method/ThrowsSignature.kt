package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.TypeVariableSignature
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ThrowsSignature:
 *   ^ [ClassTypeSignature]
 *   ^ [TypeVariableSignature]
 */
@JvmInline
value class ThrowsSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<ThrowsSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == '^'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('^'))
            if (ClassTypeSignature.shouldRead(reader.copy())) {
                append(ClassTypeSignature.read(reader))
            } else {
                append(TypeVariableSignature.read(reader))
            }
        }

        override fun unchecked(value: String) = ThrowsSignature(value)
    }

    fun isClassTypeSignature() = value[1] == 'L'

    fun isTypeVariableSignature() = value[1] == 'T'

    fun getClassTypeSignature() = ClassTypeSignature.unchecked(value.substring(1))

    fun getTypeVariableSignature() = TypeVariableSignature.unchecked(value.substring(1))

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("^")
            if (isClassTypeSignature()) {
                getClassTypeSignature().accept(visitor)
            } else {
                getTypeVariableSignature().accept(visitor)
            }
        }
    }

    override fun toString() = value

}