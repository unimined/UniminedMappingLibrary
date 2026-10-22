package xyz.wagyourtail.unimined.mapping.jvms.four.three.two

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

/**
 * ComponentType:
 *   FieldType
 */
@JvmInline
value class ComponentType private constructor(val value: FieldType) : Type {

    companion object: TypeCompanion<ComponentType> {

        override fun shouldRead(reader: CharReader<*>) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(FieldType.read(reader))
        }

        override fun unchecked(value: String) = ComponentType(FieldType.unchecked(value))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()
}