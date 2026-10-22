package xyz.wagyourtail.unimined.mapping.jvms.four.three.three

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldType
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

/**
 * ParameterDescriptor:
 *   [FieldType]
 */
@JvmInline
value class ParameterDescriptor private constructor(val value: FieldType) : Type {

    companion object: TypeCompanion<ParameterDescriptor> {

        override fun shouldRead(reader: CharReader<*>) = FieldType.shouldRead(reader)

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(FieldType.read(reader))
        }

        override fun unchecked(value: String) = ParameterDescriptor(FieldType.unchecked(value))
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}