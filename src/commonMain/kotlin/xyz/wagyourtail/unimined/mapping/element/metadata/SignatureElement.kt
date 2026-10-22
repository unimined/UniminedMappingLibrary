package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SignatureElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.*

interface SignatureElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val value: Signature

    companion object {
        operator fun invoke(value: Signature, baseNs: Namespace): SignatureElement =
            SignatureElementData(value, baseNs)
    }

}
