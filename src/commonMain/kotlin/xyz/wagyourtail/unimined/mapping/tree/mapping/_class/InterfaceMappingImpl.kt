package xyz.wagyourtail.unimined.mapping.tree.mapping._class

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyInterfaceMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InterfaceMapping
import xyz.wagyourtail.unimined.mapping.visitor.InterfaceMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AddRemove
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateInterfaceMappingVisitor

class InterfaceMappingImpl(
    parent: BaseMappingImpl<ClassMappingVisitor, *>?,
    override val type: AddRemove,
    override val name: ClassTypeSignature,
    override val baseNs: Namespace
): BaseMappingImpl<InterfaceMappingVisitor, ClassMappingVisitor>(parent), InterfaceMapping, InterfaceMappingVisitor {

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): InterfaceMappingVisitor? {
        return visitor.visitInterface(type, name, baseNs)
    }

    override fun toUMF(inner: Boolean) = buildString{
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitInterface(EmptyClassMappingVisitor(), type, name, baseNs)
        if (inner) acceptInner(DelegateInterfaceMappingVisitor(EmptyInterfaceMappingVisitor(), delegator), root.namespaces, true)
    }

}