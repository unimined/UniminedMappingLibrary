package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SignatureElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature

interface SignatureElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val value: Signature

    companion object {
        operator fun invoke(value: Signature, baseNs: Namespace): SignatureElement =
            SignatureElementData(value, baseNs)
    }

}
