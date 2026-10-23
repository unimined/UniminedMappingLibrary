package xyz.wagyourtail.unimined.mapping.jvms.ext.annotation

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import kotlin.jvm.JvmInline

/**
 * EnumConstant:
 *   [ObjectType] . [AnnotationIdentifier]
 *
 */
@JvmInline
value class EnumConstant private constructor(val value: String) : Type {

    companion object: TypeCompanion<EnumConstant> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            if (!ObjectType.shouldRead(reader.copy())) {
                return false
            }
            ObjectType.read(reader)
            if (reader.exhausted()) return false
            return reader.take() == '.'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(ObjectType.read(reader))
            append(reader.expect('.'))
            append(AnnotationIdentifier.read(reader))
        }

        override fun unchecked(value: String) = EnumConstant(value)
    }

    fun getParts(): Pair<ObjectType, AnnotationIdentifier> {
        val objectType = ObjectType.unchecked(value.substringBefore('.'))
        val annotationIdentifier = AnnotationIdentifier.unchecked(value.substringAfter('.'))
        return objectType to annotationIdentifier
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val (objectType, annotationIdentifier) = getParts()
            objectType.accept(visitor)
            visitor(".")
            annotationIdentifier.accept(visitor)
        }
    }

    override fun toString() = value

}