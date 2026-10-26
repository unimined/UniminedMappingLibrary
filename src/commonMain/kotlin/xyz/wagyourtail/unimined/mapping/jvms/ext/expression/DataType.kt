package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.ParseException
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import kotlin.jvm.JvmInline

/**
 * DataType:
 *   "byte"
 *   "char"
 *   "double"
 *   "float"
 *   "int"
 *   "long"
 *   "short"
 *   "boolean"
 *   "String"
 */
@JvmInline
value class DataType(val value: String) : Type {

    companion object : TypeCompanion<DataType> {

        /** longer collisions first (`boolean` before `byte`) so mark/reset retry works */
        val types = listOf("boolean", "byte", "char", "double", "float", "int", "long", "short", "String")

        private val firstChars = types.mapTo(mutableSetOf()) { it.first() }

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() in firstChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            for (type in types.filter { it.first() == reader.peek() }) {
                reader.mark()
                try {
                    for (char in type) {
                        reader.expect(char)
                    }
                    append(type)
                    return
                } catch (e: ParseException) {
                    reader.reset()
                }
            }
            throw IllegalArgumentException("Invalid DataType")
        }

        override fun unchecked(value: String): DataType {
            return DataType(value)
        }

    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor(value)
        }
    }

    override fun toString() = value

}