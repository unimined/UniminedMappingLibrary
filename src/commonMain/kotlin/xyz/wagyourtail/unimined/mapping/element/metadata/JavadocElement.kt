package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.JavadocElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc

interface JavadocElement : NsBoundMetadata, SourceTransform {

    /** parsed doc — link refs carry `jvms` value types
     *  (`jvms.ext.javadoc` grammar); serialized as its string form */
    val value: Javadoc

    companion object {
        operator fun invoke(value: Javadoc, baseNs: Namespace): JavadocElement =
            JavadocElementData(value, baseNs)

        operator fun invoke(value: String, baseNs: Namespace): JavadocElement =
            JavadocElementData(Javadoc.unchecked(value), baseNs)
    }

}
