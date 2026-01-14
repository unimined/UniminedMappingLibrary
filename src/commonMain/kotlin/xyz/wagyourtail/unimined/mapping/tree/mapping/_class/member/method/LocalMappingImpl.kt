package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.MemberMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.EmptyLocalVariableMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.LocalVariableMapping
import xyz.wagyourtail.unimined.mapping.visitor.LocalVariableMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateLocalVariableMappingVisitor

class LocalMappingImpl<T: InvokableMappingVisitor>(
    parent: BaseMappingImpl<T, *>,
    override val lvOrd: Int,
    override val startOp: Int?
) : MemberMappingImpl<LocalVariableMappingVisitor, T>(parent),
    LocalVariableMapping, LocalVariableMappingVisitor {
    private val _names: MutableMap<Namespace, UnqualifiedName> = mutableMapOf()
    override val names: Map<Namespace, UnqualifiedName> get() = _names

    fun setNames(names: Map<Namespace, UnqualifiedName>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): LocalVariableMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitLocalVariable(lvOrd, startOp, names)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitLocalVariable(EmptyMethodMappingVisitor(), lvOrd, startOp, names)
        if (inner) acceptInner(DelegateLocalVariableMappingVisitor(EmptyLocalVariableMappingVisitor(), delegator), root.namespaces, true)
    }
}