package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.InterfaceElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature

interface InterfaceElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemove

    val name: ClassTypeSignature

    companion object {
        operator fun invoke(type: AddRemove, name: ClassTypeSignature, baseNs: Namespace): InterfaceElement =
            InterfaceElementData(type, name, baseNs)
    }

}
