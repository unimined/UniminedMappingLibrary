package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.*

class MethodNode(parent: ClassNode) : FieldMethodResolvable<MethodNode, MethodVisitor, MethodVisitor>(parent, ::MethodNode), MethodVisitor {
    private val _params: MutableList<ParameterNode> = mutableListOf()
    private val _locals: MutableList<LocalNode> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode> = mutableListOf()

    val params: List<ParameterNode> get() = _params
    val locals: List<LocalNode> get() = _locals
    val exceptions: List<ExceptionNode> get() = _exceptions


    fun getMethodDesc(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun setMethodDescs(descs: Map<Namespace, MethodDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldOrMethodDescriptor.unchecked(it.value.toString()) })
    }

    class ParameterNode(parent: MethodNode, val index: Int?, val lvOrd: Int?) : MemberNode<ParameterVisitor, MethodVisitor, MethodVisitor>(parent), ParameterVisitor {
        private val _names: MutableMap<Namespace, String> = mutableMapOf()
        val names: Map<Namespace, String> get() = _names

        fun setNames(names: Map<Namespace, String>) {
            root.mergeNs(names.keys)
            this._names.putAll(names)
        }

        override fun acceptOuter(visitor: MethodVisitor, nsFilter: List<Namespace>, minimize: Boolean) = visitor.visitParameter(index, lvOrd, names)
    }

    class LocalNode(parent: MethodNode, val lvOrd: Int, val startOp: Int?) : MemberNode<LocalVariableVisitor, MethodVisitor, MethodVisitor>(parent), LocalVariableVisitor {
        private val _names: MutableMap<Namespace, String> = mutableMapOf()
        val names: Map<Namespace, String> get() = _names

        fun setNames(names: Map<Namespace, String>) {
            root.mergeNs(names.keys)
            this._names.putAll(names)
        }

        override fun acceptOuter(visitor: MethodVisitor, nsFilter: List<Namespace>, minimize: Boolean) = visitor.visitLocalVariable(lvOrd, startOp, names)
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
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
        val node = ExceptionNode(this, type, exception, baseNs)
        node.addNamespaces(namespaces)
        _exceptions.add(node)
        return node
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: List<Namespace>, minimize: Boolean): MethodVisitor? {
        return visitor.visitMethod(if (minimize) {
            val descNs = nsFilter.firstOrNull { it in names }
            if (descNs != null) {
                names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name to if (ns == descNs) getMethodDesc(ns) else null }
            } else {
                names.filterKeys { it in nsFilter }.mapValues { (_, name) -> name to null }
            }
        } else {
            names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let { name to descs[ns]?.getMethodDescriptor() } }
        })
    }

    override fun acceptInner(visitor: MethodVisitor, nsFilter: List<Namespace>, minimize: Boolean) {
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
}