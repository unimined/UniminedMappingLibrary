package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import kotlin.jvm.JvmInline

/**
 * FieldExpression:
 *   [ObjectType] ["this"] "." [UnqualifiedName] ";" [[FieldDescriptor]]
 *   "this." [UnqualifiedName] ";" [[FieldDescriptor]]
 */
@JvmInline
value class FieldExpression(val value: String) : Type {

    companion object : TypeCompanion<FieldExpression> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            val first = reader.peek()
            if (first == 'L') {
                reader.take()
                return true
            }
            for (char in "this.") {
                if (reader.peek() != char) return false
                reader.take()
            }
            return true
        }

        override fun read(reader: CharReader<*>) = try {
            FieldExpression(buildString {
                val first = reader.peek()
                val hasOwner = first == 'L'
                if (hasOwner) {
                    append(ObjectType.read(reader))
                }
                if (reader.peek() == '.') {
                    if (!hasOwner) throw IllegalArgumentException("expected ObjectType or \"this.\"")
                    append(reader.take())
                    append(UnqualifiedName.read(reader))
                } else {
                    val uqn = UnqualifiedName.read(reader)
                    if (uqn.value == "this" && reader.peek() == '.') {
                        append(uqn)
                        append(reader.take())
                        append(UnqualifiedName.read(reader))
                    } else {
                        append(uqn)
                    }
                }
                reader.expect(';')
                if (FieldDescriptor.shouldRead(reader.copy())) {
                    append(FieldDescriptor.read(reader))
                }
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid field expression", e)
        }

        override fun unchecked(value: String): FieldExpression {
            return FieldExpression(value)
        }

    }

    fun getParts(): Triple<ObjectType?, Boolean, Pair<UnqualifiedName, FieldDescriptor?>> {
        val (owner, rest) = if (value.startsWith("L")) {
            val (owner, part) = value.split(';', limit = 2)
            ObjectType.unchecked("$owner;") to part
        } else {
            null to value
        }
        val instance = rest.startsWith("this.")
        val (name, desc) = rest.removePrefix("this.").split(';', limit = 2)
        val fieldDesc = if (desc.isNotEmpty()) {
            FieldDescriptor.unchecked(desc)
        } else {
            null
        }
        return Triple(owner, instance, UnqualifiedName.unchecked(name) to fieldDesc)
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (owner, instance, nameAndDesc) = getParts()
            val (name, desc) = nameAndDesc
            owner?.accept(visitor)
            if (instance) {
                visitor("this.")
            }
            name.accept(visitor)
            desc?.accept(visitor)
        }
    }

    override fun toString() = value
}