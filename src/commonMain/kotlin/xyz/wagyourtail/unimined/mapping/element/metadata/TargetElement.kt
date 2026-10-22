package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.TargetElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName

interface TargetElement : MetadataElement, SourceTransform, BytecodeTransform {

    /** null means global */
    val target: FullyQualifiedName?

    val paramIdx: Int?

    companion object {
        operator fun invoke(target: FullyQualifiedName?, paramIdx: Int?): TargetElement =
            TargetElementData(target, paramIdx)
    }

}
