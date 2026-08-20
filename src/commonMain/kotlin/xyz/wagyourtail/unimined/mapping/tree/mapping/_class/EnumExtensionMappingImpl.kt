package xyz.wagyourtail.unimined.mapping.tree.mapping._class

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyEnumExtensionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EnumExtensionMapping
import xyz.wagyourtail.unimined.mapping.visitor.EnumExtensionMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AddRemove
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateEnumExtensionMappingVisitor

class EnumExtensionMappingImpl(
    parent: BaseMappingImpl<ClassMappingVisitor, *>?,
    override val type: AddRemove,
    override val name: UnqualifiedName,
    override val baseNs: Namespace
): BaseMappingImpl<EnumExtensionMappingVisitor, ClassMappingVisitor>(parent), EnumExtensionMapping, EnumExtensionMappingVisitor {

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): EnumExtensionMappingVisitor? {
        return visitor.visitEnumExtension(type, name, baseNs)
    }

    override fun toUMF(inner: Boolean) = buildString{
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.visitEnumExtension(EmptyClassMappingVisitor(), type, name, baseNs)
        if (inner) acceptInner(DelegateEnumExtensionMappingVisitor(EmptyEnumExtensionMappingVisitor(), delegator), root.namespaces, true)
    }

}
