package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.mapping.SignatureMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.ExceptionMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.LocalMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method.ParameterMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateMethodMappingVisitor

class MethodMappingImpl(parent: ClassMappingImpl) : FieldMethodResolvable<MethodMappingImpl, MethodMappingVisitor, MethodDescriptor>(parent, ::MethodMappingImpl), MethodMapping, MethodMappingVisitor {

    private val _signatures = mutableListOf<SignatureMappingImpl<MethodMappingVisitor, MethodSignature>>()
    private val _localVariables: MutableList<LocalMappingImpl<MethodMappingVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionMappingImpl<MethodMappingVisitor>> = mutableListOf()
    private val _parameters = LazyResolvables<ParameterMappingVisitor, ParameterMappingImpl<MethodMappingVisitor>>(root)

    override val parameters: List<ParameterMappingImpl<MethodMappingVisitor>> get() = _parameters.resolve()
    override val signatures: List<SignatureMappingImpl<MethodMappingVisitor, MethodSignature>> get() = _signatures
    override val localVariables: List<LocalMappingImpl<MethodMappingVisitor>> get() = _localVariables
    override val exceptions: List<ExceptionMappingImpl<MethodMappingVisitor>> get() = _exceptions

    val isClinit by lazy {
        names.values.any { it.value == "<clinit>" }
    }

    val isInit by lazy {
        names.values.any { it.value == "<init>" }
    }

    override fun setNames(names: Map<Namespace, UnqualifiedName>) {
        if (isClinit && names.values.any { it.value != "<clinit>" }) {
            throw IllegalStateException("clinit method name must be <clinit>")
        }
        if (isInit && names.values.any { it.value != "<init>" }) {
            throw IllegalStateException("init method name must be <init>")
        }
        super.setNames(names)
    }

    fun getMethodDesc(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun setMethodDescs(descs: Map<Namespace, MethodDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { MethodDescriptor.unchecked(it.value.toString()) })
    }

    override fun visitSignature(value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor {
        val node = SignatureMappingImpl(this, value, baseNs)
        _signatures.add(node)
        return node
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor {
        val newParam = ParameterMappingImpl(this, index, lvOrd)
        newParam.setNames(names)
        _parameters.addUnresolved(newParam)
        return newParam
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor {
        for (local in localVariables) {
            if (lvOrd == local.lvOrd && startOp == local.startOp) {
                local.setNames(names)
                return local
            }
        }
        val newLocal = LocalMappingImpl(this, lvOrd, startOp)
        newLocal.setNames(names)
        _localVariables.add(newLocal)
        return newLocal
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace
    ): ExceptionMappingVisitor {
        val node = ExceptionMappingImpl(this, type, exception, baseNs)
        _exceptions.add(node)
        return node
    }

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): MethodMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let {
            MethodNameAndDescriptor(name, getMethodDesc(ns))
        } }
        if (names.isEmpty()) return null
        return visitor.visitMethod(names)
    }

    override fun acceptInner(visitor: MethodMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (signature in if (sort) signatures.sortedBy { it.toString() } else signatures) {
            signature.accept(visitor, nsFilter, sort)
        }
        for (exception in if (sort) exceptions.sortedBy { it.toString() } else exceptions) {
            exception.accept(visitor, nsFilter, sort)
        }
        for (param in if (sort) parameters.sortedBy { it.toString() } else parameters) {
            param.accept(visitor, nsFilter, sort)
        }
        for (local in if (sort) localVariables.sortedBy { it.toString() } else localVariables) {
            local.accept(visitor, nsFilter, sort)
        }
    }

    override fun namesMatch(element: MethodMappingImpl): NameMatch {
        if (element.isClinit && isClinit) return NameMatch.FULL
        if (element.isInit && isInit) return NameMatch.FULL
        return super.namesMatch(element)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitMethod(EmptyClassMappingVisitor(), names.mapValues { MethodNameAndDescriptor(it.value, getMethodDesc(it.key)) })
        if (inner) acceptInner(DelegateMethodMappingVisitor(EmptyMethodMappingVisitor(), delegator), root.namespaces, true)
    }

}