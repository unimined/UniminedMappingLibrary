package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.AddRemoveClear
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SealElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface SealElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemoveClear

    val name: InternalName?

    companion object {
        operator fun invoke(type: AddRemoveClear, name: InternalName?, baseNs: Namespace): SealElement =
            SealElementData(type, name, baseNs)
    }

}
