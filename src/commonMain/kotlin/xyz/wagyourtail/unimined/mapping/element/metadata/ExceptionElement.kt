package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ExceptionElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

interface ExceptionElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemove

    val exception: InternalName

    companion object {
        operator fun invoke(type: AddRemove, exception: InternalName, baseNs: Namespace): ExceptionElement =
            ExceptionElementData(type, exception, baseNs)
    }

}
