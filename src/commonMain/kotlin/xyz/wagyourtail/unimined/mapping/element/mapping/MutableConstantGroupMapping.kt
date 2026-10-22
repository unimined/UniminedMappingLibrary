package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.ConstantGroupMappingData
import xyz.wagyourtail.unimined.mapping.element.metadata.ConstantElement
import xyz.wagyourtail.unimined.mapping.element.metadata.TargetElement
import xyz.wagyourtail.unimined.mapping.element.metadata.ExpressionElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.*

interface MutableConstantGroupMapping : ConstantGroupMapping, MutableMetadataParent {

    fun constant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantElement

    /** @param target null means global */
    fun target(target: FullyQualifiedName?, paramIdx: Int?): TargetElement

    fun expression(value: Constant, expression: Expression): ExpressionElement

    companion object {
        operator fun invoke(
            type: InlineType,
            name: String? = null,
            baseNs: Namespace,
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableConstantGroupMapping = ConstantGroupMappingData(
            type,
            name,
            baseNs,
            metadata.asMetadataDataList()
        )
    }

}
