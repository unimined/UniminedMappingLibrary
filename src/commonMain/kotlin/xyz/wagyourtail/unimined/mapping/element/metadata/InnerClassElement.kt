package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.InnerType
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.InnerClassElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName

interface InnerClassElement : MetadataElement, MetadataParent, SourceTransform, BytecodeTransform {

    val type: InnerType

    val names: Map<Namespace, Pair<String, FullyQualifiedName?>>

    companion object {
        operator fun invoke(
            type: InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>> = emptyMap()
        ): InnerClassElement = InnerClassElementData(type, names.toMutableMap())
    }

}
