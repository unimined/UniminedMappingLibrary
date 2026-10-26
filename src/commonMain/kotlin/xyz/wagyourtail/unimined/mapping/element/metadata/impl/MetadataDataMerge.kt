package xyz.wagyourtail.unimined.mapping.element.metadata.impl

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.toNsMap

/** shared metadata-attach merge used by all MutableMetadataParent impls */
internal fun MutableList<MetadataElementData>.mergeMetadata(meta: MetadataElement) {
    meta as MetadataElementData
    when (meta) {
        is JavadocElement -> {
            if (none { it is JavadocElement && it.value == meta.value }) add(meta)
        }
        is InnerClassElement -> {
            val existing = filterIsInstance<InnerClassElement>().firstOrNull { it.type == meta.type }
            if (existing is InnerClassElementData) existing.setNames(meta.names) else add(meta)
        }
        is SignatureElement, is AccessElement, is AnnotationElement, is ExceptionElement,
        is SealElement, is InterfaceElement, is EnumExtensionElement,
        is ConstantElement, is TargetElement, is ExpressionElement,
        is SyntheticBridgeElement -> add(meta)
    }
}

/** wrap a foreign [MetadataElement] impl in the serializable default impl */
internal fun MetadataElement.asMetadataData(): MetadataElementData {
    if (this is MetadataElementData) return this
    return when (this) {
        is JavadocElement -> JavadocElementData(value, baseNs)
        is SignatureElement -> SignatureElementData(value, baseNs)
        is AccessElement -> AccessElementData(type, value, condition)
        is AnnotationElement -> AnnotationElementData(type, baseNs, annotation)
        is ExceptionElement -> ExceptionElementData(type, exception, baseNs)
        is SealElement -> SealElementData(type, name, baseNs)
        is InterfaceElement -> InterfaceElementData(type, name, baseNs)
        is EnumExtensionElement -> EnumExtensionElementData(type, name, baseNs)
        is InnerClassElement -> InnerClassElementData(type, names.toNsMap(), metadata.asMetadataDataList())
        is ConstantElement -> ConstantElementData(owner, field)
        is TargetElement -> TargetElementData(target, paramIdx)
        is ExpressionElement -> ExpressionElementData(value, expression)
        is SyntheticBridgeElement -> SyntheticBridgeElementData(names.toNsMap())
        else -> error("no default impl for ${this::class}")
    }
}

internal fun Iterable<MetadataElement>.asMetadataDataList(): MutableList<MetadataElementData> =
    map { it.asMetadataData() }.toMutableList()
