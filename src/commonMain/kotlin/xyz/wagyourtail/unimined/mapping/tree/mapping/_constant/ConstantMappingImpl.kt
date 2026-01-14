package xyz.wagyourtail.unimined.mapping.tree.mapping._constant

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantMapping
import xyz.wagyourtail.unimined.mapping.visitor.ConstantMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyConstantMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateConstantMappingVisitor

class ConstantMappingImpl(
    parent: ConstantGroupMappingImpl,
    val baseNs: Namespace,
    override val owner: InternalName,
    override val field: FieldNameAndDescriptor
) : BaseMappingImpl<ConstantMappingVisitor, ConstantGroupMappingVisitor>(parent),
    ConstantMapping, ConstantMappingVisitor {

    override fun acceptOuter(visitor: ConstantGroupMappingVisitor, nsFilter: Collection<Namespace>): ConstantMappingVisitor? {
        return if (baseNs !in nsFilter) {
//            val ns = nsFilter.filter { it in (parent as ConstantGroupMappingImpl).namespaces }.toSet()
//            if (ns.isEmpty()) return null
//            val first = ns.first()
//            val mapped = root.map(baseNs, first,
//                FullyQualifiedName(
//                    ObjectType(owner),
//                    field.asNameAndDescriptor()
//                )
//            )
//            val (constClass, constName) = mapped.getParts()
//            val (fieldName, fieldDesc) = constName!!.getParts()
//            return visitor.visitConstant(constClass.getInternalName(), fieldName, fieldDesc?.getFieldDescriptor())
            null
        } else {
            return visitor.visitConstant(owner, field.name, field.descriptor)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitConstant(EmptyConstantGroupMappingVisitor(), owner, field.name, field.descriptor)
        if (inner) acceptInner(DelegateConstantMappingVisitor(EmptyConstantMappingVisitor(), delegator), root.namespaces, true)
    }

}