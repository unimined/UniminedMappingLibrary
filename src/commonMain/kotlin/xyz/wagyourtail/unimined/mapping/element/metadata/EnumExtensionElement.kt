package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.EnumExtensionElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface EnumExtensionElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemove

    val name: UnqualifiedName

    companion object {
        operator fun invoke(type: AddRemove, name: UnqualifiedName, baseNs: Namespace): EnumExtensionElement =
            EnumExtensionElementData(type, name, baseNs)
    }

}
