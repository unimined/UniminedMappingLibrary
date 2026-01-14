package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.EmptyExceptionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionMapping
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateExceptionMappingVisitor

class ExceptionMappingImpl<T: InvokableMappingVisitor>(
    parent: BaseMappingImpl<T, *>,
    override val type: ExceptionType,
    override val exception: InternalName,
    override val baseNs: Namespace
) : BaseMappingImpl<ExceptionMappingVisitor, T>(parent), ExceptionMapping, ExceptionMappingVisitor {

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): ExceptionMappingVisitor? {
        return if (baseNs !in nsFilter) {
            null
        } else {
            visitor.visitException(type, exception, baseNs)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitException(EmptyMethodMappingVisitor(), type, exception, baseNs)
        if (inner) acceptInner(DelegateExceptionMappingVisitor(EmptyExceptionMappingVisitor(), delegator), root.namespaces, true)
    }

}