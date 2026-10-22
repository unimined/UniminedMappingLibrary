package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ConstantElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

interface ConstantElement : MetadataElement, SourceTransform, BytecodeTransform {

    val owner: InternalName

    val field: FieldNameAndDescriptor

    companion object {
        operator fun invoke(owner: InternalName, field: FieldNameAndDescriptor): ConstantElement =
            ConstantElementData(owner, field)
    }

}
