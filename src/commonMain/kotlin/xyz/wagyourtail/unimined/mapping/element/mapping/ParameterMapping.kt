package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface ParameterMapping : MappingElement, MetadataParent {

    val index: Int?

    val lvOrd: Int?

    val names: Map<Namespace, UnqualifiedName>

    companion object {
        operator fun invoke(
            index: Int? = null,
            lvOrd: Int? = null,
            names: Map<Namespace, UnqualifiedName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): ParameterMapping = MutableParameterMapping(index, lvOrd, names, metadata)
    }

}
