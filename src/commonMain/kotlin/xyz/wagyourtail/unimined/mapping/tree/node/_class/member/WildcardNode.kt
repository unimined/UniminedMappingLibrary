package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ExceptionNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.LocalNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ParameterNode
import xyz.wagyourtail.unimined.mapping.visitor.*

class WildcardNode(parent: ClassNode, val type: WildcardType, val descs: Map<Namespace, FieldOrMethodDescriptor>) : MemberNode<WildcardVisitor, WildcardVisitor, ClassVisitor>(parent), WildcardVisitor {
    private val _params: MutableList<ParameterNode<WildcardVisitor>> = mutableListOf()
    private val _locals: MutableList<LocalNode<WildcardVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<WildcardVisitor>> = mutableListOf()

    val params: List<ParameterNode<WildcardVisitor>> get() = _params
    val locals: List<LocalNode<WildcardVisitor>> get() = _locals
    val exceptions: List<ExceptionNode<WildcardVisitor>> get() = _exceptions

    fun getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return root.mapDescriptor(fromNs, namespace, descs[fromNs]!!)
    }

    fun getMethodDescriptor(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun getFieldDescriptor(namespace: Namespace) = getDescriptor(namespace)?.getFieldDescriptor()

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        if (type == WildcardType.FIELD) return null
        for (param in params) {
            if (index != null && param.index == index) {
                param.setNames(names)
                return param
            }
            if (lvOrd != null && param.lvOrd == lvOrd) {
                param.setNames(names)
                return param
            }
        }
        val newParam = ParameterNode(this, index, lvOrd)
        newParam.setNames(names)
        _params.add(newParam)
        return newParam
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        if (type == WildcardType.FIELD) return null
        for (local in locals) {
            if (lvOrd == local.lvOrd && startOp == local.startOp) {
                local.setNames(names)
                return local
            }
        }
        val newLocal = LocalNode(this, lvOrd, startOp)
        newLocal.setNames(names)
        _locals.add(newLocal)
        return newLocal
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        if (this.type == WildcardType.FIELD) return null
        val node = ExceptionNode(this, type, exception, baseNs)
        node.addNamespaces(namespaces)
        _exceptions.add(node)
        return node
    }
    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>, minimize: Boolean): WildcardVisitor? {
        if (descs.isEmpty()) return visitor.visitWildcard(type, emptyMap())
        val desc = if (minimize) {
            val descNs = nsFilter.firstOrNull { it in this.descs }
            if (descNs != null) {
                val ns = nsFilter.first()
                mapOf(ns to root.mapDescriptor(descNs, ns, this.descs[descNs]!!))
            } else {
                val fromNs = descs.keys.first()
                val ns = nsFilter.first()
                mapOf(ns to root.mapDescriptor(fromNs, ns, descs[fromNs]!!))
            }
        } else {
            if (descs.keys.none { it in nsFilter }) {
                val fromNs = descs.keys.first()
                val ns = nsFilter.first()
                mapOf(ns to root.mapDescriptor(fromNs, ns, descs[fromNs]!!))
            } else {
                descs.filter { it.key in nsFilter }
            }
        }
        return visitor.visitWildcard(type, desc)
    }

    override fun acceptInner(visitor: WildcardVisitor, nsFilter: Collection<Namespace>, minimize: Boolean) {
        super.acceptInner(visitor, nsFilter, minimize)
        for (exception in exceptions) {
            exception.accept(visitor, nsFilter, minimize)
        }
        for (param in params) {
            param.accept(visitor, nsFilter, minimize)
        }
        for (local in locals) {
            local.accept(visitor, nsFilter, minimize)
        }
    }

    enum class WildcardType {
        METHOD,
        FIELD
        ;
    }


}