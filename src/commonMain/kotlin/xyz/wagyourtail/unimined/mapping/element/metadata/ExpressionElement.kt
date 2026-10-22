package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ExpressionElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression

interface ExpressionElement : MetadataElement, SourceTransform, BytecodeTransform {

    val value: Constant

    val expression: Expression

    companion object {
        operator fun invoke(value: Constant, expression: Expression): ExpressionElement =
            ExpressionElementData(value, expression)
    }

}
