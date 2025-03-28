package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.node.SignatureNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ExceptionNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.LocalNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ParameterNode
import xyz.wagyourtail.unimined.mapping.visitor.*

class WildcardNode(parent: ClassNode, val type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>) : MemberNode<WildcardVisitor, ClassVisitor>(parent), WildcardVisitor, LazyResolvableEntry<WildcardNode, WildcardVisitor> {
    private val _descs = descs.toMutableMap()
    private val _signatures: MutableSet<SignatureNode<WildcardVisitor>> = mutableSetOf()
    private val _locals: MutableList<LocalNode<WildcardVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<WildcardVisitor>> = mutableListOf()

    val descs: Map<Namespace, FieldOrMethodDescriptor> get() = _descs
    val signatures: Set<SignatureNode<WildcardVisitor>> get() = _signatures
    val params = LazyResolvables<ParameterVisitor, ParameterNode<WildcardVisitor>>(root) {
        ParameterNode(this, null, null)
    }
    val locals: List<LocalNode<WildcardVisitor>> get() = _locals
    val exceptions: List<ExceptionNode<WildcardVisitor>> get() = _exceptions

    fun getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return root.map(fromNs, namespace, descs[fromNs]!!)
    }

    fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>) {
        root.mergeNs(descs.keys)
        this._descs.putAll(descs)
    }

    fun getMethodDescriptor(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun getFieldDescriptor(namespace: Namespace) = getDescriptor(namespace)?.getFieldDescriptor()


    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor {
        val node = SignatureNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _signatures.add(node)
        return node
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        if (type == WildcardType.FIELD) return null
        val newParam = ParameterNode(this, index, lvOrd)
        newParam.setNames(names)
        params.addUnresolved(newParam)
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
        for (signature in signatures) {
            signature.accept(visitor, nsFilter)
        }
        for (exception in exceptions) {
            exception.accept(visitor, nsFilter)
        }
        for (param in params.resolve()) {
            if (param.names.isEmpty()) continue
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