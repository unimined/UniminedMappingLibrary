package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateExceptionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateLocalVariableVisitor

class LocalNode<T: InvokableVisitor<T>>(parent: BaseNode<T, *>, val lvOrd: Int, val startOp: Int?) : MemberNode<LocalVariableVisitor, T>(parent),
    LocalVariableVisitor {
    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    val names: Map<Namespace, String> get() = _names

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): LocalVariableVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitLocalVariable(lvOrd, startOp, names)
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitLocalVariable(EmptyMethodVisitor(), lvOrd, startOp, names)
//        acceptInner(DelegateLocalVariableVisitor(EmptyLocalVariableVisitor(), delegator), root.namespaces)
    }
}