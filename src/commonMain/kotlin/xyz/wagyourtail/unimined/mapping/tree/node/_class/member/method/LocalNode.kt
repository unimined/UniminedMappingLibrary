package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.LocalVariableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor

class LocalNode<T: InvokableVisitor<T>>(parent: BaseNode<T, *>, val lvOrd: Int, val startOp: Int?) : MemberNode<LocalVariableVisitor, MethodVisitor, T>(parent),
    LocalVariableVisitor {
    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    val names: Map<Namespace, String> get() = _names

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>, minimize: Boolean): LocalVariableVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitLocalVariable(lvOrd, startOp, names)
    }
}