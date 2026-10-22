package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.*
interface LocalVariableMapping : MappingElement, MetadataParent {

    val lvOrd: Int

    val startOp: Int?

    val names: Map<Namespace, UnqualifiedName>

    companion object {
        operator fun invoke(
            lvOrd: Int,
            startOp: Int? = null,
            names: Map<Namespace, UnqualifiedName> = emptyMap(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): LocalVariableMapping = MutableLocalVariableMapping(lvOrd, startOp, names, metadata)
    }

}
