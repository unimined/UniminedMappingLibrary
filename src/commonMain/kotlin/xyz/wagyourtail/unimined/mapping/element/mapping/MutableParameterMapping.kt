package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.ParameterMappingData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.toSmallMap

interface MutableParameterMapping : ParameterMapping, MutableMetadataParent {

    override var index: Int?

    override var lvOrd: Int?

    fun setNames(names: Map<Namespace, UnqualifiedName>)

    companion object {
        operator fun invoke(
            index: Int? = null,
            lvOrd: Int? = null,
            names: Map<Namespace, UnqualifiedName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableParameterMapping = ParameterMappingData(
            index,
            lvOrd,
            names.toSmallMap(),
            metadata.asMetadataDataList()
        )
    }

}
