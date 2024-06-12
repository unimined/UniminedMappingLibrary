package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ParameterVisitor

class ParameterNode<T: InvokableVisitor<T>>(parent: BaseNode<T, *>, val index: Int?, val lvOrd: Int?) : MemberNode<ParameterVisitor, MethodVisitor, T>(parent),
    ParameterVisitor {
    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    val names: Map<Namespace, String> get() = _names

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, minimize: Boolean) = visitor.visitParameter(index, lvOrd, names)
}