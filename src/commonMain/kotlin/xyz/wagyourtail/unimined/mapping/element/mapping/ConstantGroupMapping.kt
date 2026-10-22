package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.element.metadata.ConstantElement
import xyz.wagyourtail.unimined.mapping.element.metadata.TargetElement
import xyz.wagyourtail.unimined.mapping.element.metadata.ExpressionElement
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.*
interface ConstantGroupMapping : MappingElement, MetadataParent {

    val type: InlineType

    val name: String?

    /** the namespace the constant/target/expression values are relative to */
    val baseNs: Namespace

    val constants: Iterable<ConstantElement>
        get() = metadata.filterIsInstance<ConstantElement>()

    val targets: Iterable<TargetElement>
        get() = metadata.filterIsInstance<TargetElement>()

    val expressions: Iterable<ExpressionElement>
        get() = metadata.filterIsInstance<ExpressionElement>()

    companion object {
        operator fun invoke(
            type: InlineType,
            name: String? = null,
            baseNs: Namespace,
            metadata: Iterable<MetadataElement> = emptyList()
        ): ConstantGroupMapping = MutableConstantGroupMapping(type, name, baseNs, metadata)
    }

}
