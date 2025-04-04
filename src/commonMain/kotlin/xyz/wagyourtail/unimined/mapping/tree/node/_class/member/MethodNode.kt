package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.node.SignatureNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ExceptionNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.LocalNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ParameterNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateMethodVisitor

class MethodNode(parent: ClassNode) : FieldMethodResolvable<MethodNode, MethodVisitor>(parent, ::MethodNode), MethodVisitor {

    private val _signatures = mutableSetOf<SignatureNode<MethodVisitor>>()
    private val _locals: MutableList<LocalNode<MethodVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<MethodVisitor>> = mutableListOf()

    val signatures: Set<SignatureNode<MethodVisitor>> get() = _signatures
    val params = LazyResolvables<ParameterVisitor, ParameterNode<MethodVisitor>>(root)
    val locals: List<LocalNode<MethodVisitor>> get() = _locals
    val exceptions: List<ExceptionNode<MethodVisitor>> get() = _exceptions

    val isClinit by lazy {
        names.values.any { it == "<clinit>" }
    }

    val isInit by lazy {
        names.values.any { it == "<init>" }
    }

    override fun setNames(names: Map<Namespace, String>) {
        super.setNames(names)
        if (isClinit && names.values.any { it != "<clinit>" }) {
            throw IllegalStateException("clinit method name must be <clinit>")
        }
        if (isInit && names.values.any { it != "<init>" }) {
            throw IllegalStateException("init method name must be <init>")
        }
        if (names.values.any { it.isEmpty() }) {
            throw IllegalStateException("empty name in $names")
        }
    }

    fun getMethodDesc(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun setMethodDescs(descs: Map<Namespace, MethodDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldOrMethodDescriptor.unchecked(it.value.toString()) })
    }

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor {
        val node = SignatureNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _signatures.add(node)
        return node
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor {
        val newParam = ParameterNode(this, index, lvOrd)
        newParam.setNames(names)
        params.addUnresolved(newParam)
        return newParam
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor {
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
    ): ExceptionVisitor {
        val node = ExceptionNode(this, type, exception, baseNs)
        node.addNamespaces(namespaces)
        _exceptions.add(node)
        return node
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): MethodVisitor? {
        val names = names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let { name to getMethodDesc(ns) } }
        if (names.isEmpty()) return null
        return visitor.visitMethod(names)
    }

    override fun acceptInner(visitor: MethodVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (signature in if (sort) signatures.sortedBy { it.toString() } else signatures) {
            signature.accept(visitor, nsFilter, sort)
        }
        for (exception in if (sort) exceptions.sortedBy { it.toString() } else exceptions) {
            exception.accept(visitor, nsFilter, sort)
        }
        for (param in if (sort) params.resolve().sortedBy { it.toString() } else params.resolve()) {
            param.accept(visitor, nsFilter, sort)
        }
        for (local in if (sort) locals.sortedBy { it.toString() } else locals) {
            local.accept(visitor, nsFilter, sort)
        }
    }

    override fun namesMatch(element: MethodNode): NameMatch {
        if (element.isClinit && isClinit) return NameMatch.FULL
        if (element.isInit && isInit) return NameMatch.FULL
        return super.namesMatch(element)
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitMethod(EmptyClassVisitor(), names.mapValues { it.value to getMethodDesc(it.key) })
        if (inner) acceptInner(DelegateMethodVisitor(EmptyMethodVisitor(), delegator), root.namespaces, true)
    }

}