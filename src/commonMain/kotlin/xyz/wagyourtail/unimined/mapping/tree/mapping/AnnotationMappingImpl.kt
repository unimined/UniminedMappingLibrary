package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationType
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationMapping
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AnnotationMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAnnotationParentMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyAnnotationMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateAnnotationMappingVisitor

class AnnotationMappingImpl<U: AnnotationParentMappingVisitor>(
    parent: BaseMappingImpl<U, *>,
    override val type: AnnotationType,
    override val baseNs: Namespace,
    override val annotation: Annotation
) : BaseMappingImpl<AnnotationMappingVisitor, U>(parent), AnnotationMapping, AnnotationMappingVisitor {

    override fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): AnnotationMappingVisitor? {
        return if (baseNs !in nsFilter) {
            null
        } else {
            visitor.visitAnnotation(type, baseNs, annotation)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitAnnotation(EmptyAnnotationParentMappingVisitor(), type, baseNs, annotation)
        if (inner) acceptInner(DelegateAnnotationMappingVisitor(EmptyAnnotationMappingVisitor(), delegator), root.namespaces, true)
    }

}