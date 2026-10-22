package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.tree.impl.LocalVariableMappingData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface MutableLocalVariableMapping : LocalVariableMapping, MutableMetadataParent {

    fun setNames(names: Map<Namespace, UnqualifiedName>)

    companion object {
        operator fun invoke(
            lvOrd: Int,
            startOp: Int? = null,
            names: Map<Namespace, UnqualifiedName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): MutableLocalVariableMapping = LocalVariableMappingData(
            lvOrd,
            startOp,
            names.toMutableMap(),
            metadata.asMetadataDataList()
        )
    }

}
