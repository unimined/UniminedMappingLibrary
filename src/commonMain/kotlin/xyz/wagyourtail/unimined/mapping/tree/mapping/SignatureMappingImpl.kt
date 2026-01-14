package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.visitor.EmptySignatureParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptySignatureMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureMapping
import xyz.wagyourtail.unimined.mapping.visitor.SignatureParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateSignatureMappingVisitor

class SignatureMappingImpl<T: SignatureParentMappingVisitor<S>, S: Signature>(
    parent: BaseMappingImpl<T, *>,
    override val value: S,
    override val baseNs: Namespace
) : BaseMappingImpl<SignatureMappingVisitor, T>(parent), SignatureMapping<S>, SignatureMappingVisitor {

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): SignatureMappingVisitor? {
        return if (baseNs !in nsFilter) {
            null
        } else {
            visitor.visitSignature(value, baseNs)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitSignature(EmptySignatureParentMappingVisitor(), value, baseNs)
        if (inner) acceptInner(DelegateSignatureMappingVisitor(EmptySignatureMappingVisitor(), delegator), root.namespaces, true)
    }

}