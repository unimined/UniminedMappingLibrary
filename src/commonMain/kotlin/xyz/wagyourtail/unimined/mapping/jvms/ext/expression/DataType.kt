package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.commonskt.reader.CharReader
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
 *   "Class"
 */
@JvmInline
value class DataType(val value: String) : Type {

    companion object : TypeCompanion<DataType> {

        val types = setOf("byte", "char", "double", "float", "int", "long", "short", "boolean", "String", "Class").associateBy { it.first() }

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return types.containsKey(reader.take())
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val type = types.getValue(reader.peek()!!)
            for (char in type) {
                reader.expect(char)
            }
            append(type)
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