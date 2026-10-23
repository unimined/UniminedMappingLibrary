package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemoveClear
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SealElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

interface SealElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemoveClear

    val name: InternalName?

    companion object {
        operator fun invoke(type: AddRemoveClear, name: InternalName?, baseNs: Namespace): SealElement =
            SealElementData(type, name, baseNs)
    }

}
