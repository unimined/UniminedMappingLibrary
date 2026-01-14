package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.mapping.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.mapping.SignatureMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.ExceptionMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.LocalMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.ParameterMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateWildcardMappingVisitor

class WildcardMappingImpl(
    parent: ClassMappingImpl,
    override val type: WildcardType,
    descs: Map<Namespace, FieldOrMethodDescriptor>
) : MemberMappingImpl<WildcardMappingVisitor, ClassMappingVisitor>(parent), WildcardMapping, LazyResolvableEntry<WildcardMappingImpl, WildcardMappingVisitor>, WildcardMappingVisitor {
    private val _descs = descs.toMutableMap()
    private val _signatures: MutableList<SignatureMappingImpl<WildcardMappingVisitor, MethodSignature>> = mutableListOf()
    private val _locals: MutableList<LocalMappingImpl<WildcardMappingVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionMappingImpl<WildcardMappingVisitor>> = mutableListOf()
    private val _parameters = LazyResolvables<ParameterMappingVisitor, ParameterMappingImpl<WildcardMappingVisitor>>(root)

    override val descs: Map<Namespace, FieldOrMethodDescriptor> get() = _descs
    override val signatures: List<SignatureMappingImpl<WildcardMappingVisitor, MethodSignature>> get() = _signatures
    override val parameters: List< ParameterMappingImpl<WildcardMappingVisitor>> get() = _parameters.resolve()
    override val localVariables: List<LocalMappingImpl<WildcardMappingVisitor>> get() = _locals
    override val exceptions: List<ExceptionMappingImpl<WildcardMappingVisitor>> get() = _exceptions

    fun hasDescriptor() = descs.isNotEmpty()

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


    override fun visitSignature(value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor {
        val node = SignatureMappingImpl(this, value, baseNs)
        _signatures.add(node)
        return node
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        if (type == WildcardType.FIELD) return null
        val newParam = ParameterMappingImpl(this, index, lvOrd)
        newParam.setNames(names)
        _parameters.addUnresolved(newParam)
        return newParam
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        if (type == WildcardType.FIELD) return null
        for (local in localVariables) {
            if (lvOrd == local.lvOrd && startOp == local.startOp) {
                local.setNames(names)
                return local
            }
        }
        val newLocal = LocalMappingImpl(this, lvOrd, startOp)
        newLocal.setNames(names)
        _locals.add(newLocal)
        return newLocal
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace
    ): ExceptionMappingVisitor? {
        if (this.type == WildcardType.FIELD) return null
        val node = ExceptionMappingImpl(this, type, exception, baseNs)
        _exceptions.add(node)
        return node
    }
    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): WildcardMappingVisitor? {
        if (descs.isEmpty()) return visitor.visitWildcard(type, emptyMap())
        return visitor.visitWildcard(type, nsFilter.associateWith { getDescriptor(it)!! })
    }

    override fun acceptInner(visitor: WildcardMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (signature in if (sort) signatures.sortedBy { it.toString() } else signatures) {
            signature.accept(visitor, nsFilter, sort)
        }
        for (exception in if (sort) exceptions.sortedBy { it.toString() } else exceptions) {
            exception.accept(visitor, nsFilter, sort)
        }
        for (param in if (sort) parameters.sortedBy { it.toString() } else parameters) {
            if (param.names.isEmpty()) continue
            param.accept(visitor, nsFilter, sort)
        }
        for (local in if (sort) localVariables.sortedBy { it.toString() } else localVariables) {
            local.accept(visitor, nsFilter, sort)
        }
    }

    fun doMerge(target: WildcardMappingImpl) {
        acceptInner(target, root.namespaces, false)
    }

    override fun merge(element: WildcardMappingImpl): Boolean {
        if (element.type != type) return false
        if (element.descs.isEmpty() && descs.isEmpty()) {
            doMerge(element)
            return true
        }
        if (element.descs.isNotEmpty() && descs.isNotEmpty()) {
            val descKey = element.descs.keys.intersect(descs.keys).firstOrNull() ?: descs.keys.first()
            if (element.getDescriptor(descKey) == descs[descKey]) {
                element.setDescriptors(descs)
                doMerge(element)
                return true
            }
        }
        return false
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitWildcard(EmptyClassMappingVisitor(), type, descs)
        if (inner) acceptInner(DelegateWildcardMappingVisitor(EmptyWildcardMappingVisitor(), delegator), root.namespaces, true)
    }

}