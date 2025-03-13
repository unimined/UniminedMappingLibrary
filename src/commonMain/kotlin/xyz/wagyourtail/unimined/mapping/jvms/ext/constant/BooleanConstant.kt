package xyz.wagyourtail.unimined.mapping.jvms.ext.constant

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion

class BooleanConstant(val value: Boolean) : Type {

    companion object : TypeCompanion<BooleanConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            val f = reader.take()
            return f == 't' || f == 'f'
        }

        override fun read(reader: CharReader<*>) = try {
            val b = when (val first = reader.peek()) {
                'f' -> {
                    for (char in "false") {
                        reader.expect(char)
                    }
                    false
                }
                't' -> {
                    for (char in "true") {
                        reader.expect(char)
                    }
                    true
                }
                else -> {
                    throw IllegalArgumentException("expected t or f, got $first")
                }
            }
            BooleanConstant(b)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid boolean constant", e)
        }

        override fun unchecked(value: String): BooleanConstant {
            return BooleanConstant(value.first() == 't')
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString(): String = value.toString()

}