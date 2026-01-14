package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.visitor.EmptyJavadocParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyJavadocMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocMapping
import xyz.wagyourtail.unimined.mapping.visitor.JavadocParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateJavadocMappingVisitor

class JavadocMappingImpl<T: JavadocParentMappingVisitor>(
    parent: BaseMappingImpl<T, *>,
    override val value: String,
    override val baseNs: Namespace
) : BaseMappingImpl<JavadocMappingVisitor, T>(parent), JavadocMapping, JavadocMappingVisitor {

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): JavadocMappingVisitor? {
        return visitor.visitJavadoc(value, baseNs)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitJavadoc(EmptyJavadocParentMappingVisitor(), value, baseNs)
        if (inner) acceptInner(DelegateJavadocMappingVisitor(EmptyJavadocMappingVisitor(), delegator), root.namespaces, true)
    }

}