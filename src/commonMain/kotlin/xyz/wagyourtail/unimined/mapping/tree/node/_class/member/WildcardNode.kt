package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ExceptionNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.LocalNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ParameterNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateFieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateWildcardVisitor

class WildcardNode(parent: ClassNode, val type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>) : MemberNode<WildcardVisitor, WildcardVisitor, ClassVisitor>(parent), WildcardVisitor, LazyResolvableEntry<WildcardNode, WildcardVisitor> {
    private val _descs = descs.toMutableMap()
    private val _params: MutableList<ParameterNode<WildcardVisitor>> = mutableListOf()
    private val _locals: MutableList<LocalNode<WildcardVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<WildcardVisitor>> = mutableListOf()

    val descs: Map<Namespace, FieldOrMethodDescriptor> get() = _descs
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

    fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>) {
        root.mergeNs(descs.keys)
        this._descs.putAll(descs)
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
    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): WildcardVisitor? {
        if (descs.isEmpty()) return visitor.visitWildcard(type, emptyMap())
        return visitor.visitWildcard(type, nsFilter.associateWith { getDescriptor(it)!! })
    }

    override fun acceptInner(visitor: WildcardVisitor, nsFilter: Collection<Namespace>) {
        super.acceptInner(visitor, nsFilter)
        for (exception in exceptions) {
            exception.accept(visitor, nsFilter)
        }
        for (param in params) {
            param.accept(visitor, nsFilter)
        }
        for (local in locals) {
            local.accept(visitor, nsFilter)
        }
    }

    enum class WildcardType {
        METHOD,
        FIELD
        ;

        fun asElementType(): ElementType {
            return when (this) {
                METHOD -> ElementType.METHOD
                FIELD -> ElementType.FIELD
            }
        }
    }

    fun doMerge(target: WildcardNode) {
        acceptInner(target, root.namespaces)
    }

    override fun merge(element: WildcardNode): WildcardNode? {
        if (element.type != type) return null
        if (element.descs.isEmpty() && descs.isEmpty()) {
            doMerge(element)
            return element
        }
        if (element.descs.isNotEmpty() && descs.isNotEmpty()) {
            val descKey = descs.keys.first()
            if (element.getDescriptor(descKey) == getDescriptor(descKey)) {
                element.setDescriptors(descs)
                doMerge(element)
                return element
            }
        }
        return null
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitWildcard(EmptyClassVisitor(), type, descs)
//        acceptInner(DelegateWildcardVisitor(EmptyWildcardVisitor(), delegator), root.namespaces)
    }

}