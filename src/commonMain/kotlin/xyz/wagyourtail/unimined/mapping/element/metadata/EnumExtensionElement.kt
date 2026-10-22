package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.EnumExtensionElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface EnumExtensionElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemove

    val name: UnqualifiedName

    companion object {
        operator fun invoke(type: AddRemove, name: UnqualifiedName, baseNs: Namespace): EnumExtensionElement =
            EnumExtensionElementData(type, name, baseNs)
    }

}
