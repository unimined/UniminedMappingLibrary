@file:UseSerializers(
    NamespaceSerializer::class,
    InternalNameSerializer::class,
    UnqualifiedNameSerializer::class,
    FieldNameAndDescriptorSerializer::class,
    MethodNameAndDescriptorSerializer::class,
    FieldOrMethodDescriptorSerializer::class,
    FieldDescriptorSerializer::class,
    MethodDescriptorSerializer::class
)

package xyz.wagyourtail.unimined.mapping.tree.impl


import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class ConstantGroupMappingData(
    override val type: InlineType,
    override val name: String? = null,
    override val baseNs: Namespace,
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableConstantGroupMapping {

    override fun constant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantElement {
        val node = ConstantElement(fieldClass, FieldNameAndDescriptor.unchecked(
            fieldName.value + if (fieldDesc != null) ";$fieldDesc" else ""
        ))
        addMetadata(node)
        return node
    }

    override fun target(target: FullyQualifiedName?, paramIdx: Int?): TargetElement {
        val node = TargetElement(target, paramIdx)
        addMetadata(node)
        return node
    }

    override fun expression(value: Constant, expression: Expression): ExpressionElement {
        val node = ExpressionElement(value, expression)
        addMetadata(node)
        return node
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

}
