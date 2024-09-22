package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import io.github.oshai.kotlinlogging.KotlinLogging
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

class MethodNode(parent: ClassNode) : FieldMethodResolvable<MethodNode, MethodVisitor>(parent, ::MethodNode), MethodVisitor {

    private val _signatures = mutableSetOf<SignatureNode<MethodVisitor>>()
    private val _locals: MutableList<LocalNode<MethodVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<MethodVisitor>> = mutableListOf()

    val signatures: Set<SignatureNode<MethodVisitor>> get() = _signatures
    val params = LazyResolvables<ParameterVisitor, ParameterNode<MethodVisitor>>(root) {
        ParameterNode(this, null, null)
    }
    val locals: List<LocalNode<MethodVisitor>> get() = _locals
    val exceptions: List<ExceptionNode<MethodVisitor>> get() = _exceptions

    override fun setNames(names: Map<Namespace, String>) {
        super.setNames(names)
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

    override fun acceptInner(visitor: MethodVisitor, nsFilter: Collection<Namespace>) {
        super.acceptInner(visitor, nsFilter)
        for (signature in _signatures) {
            signature.accept(visitor, nsFilter)
        }
        for (exception in exceptions) {
            exception.accept(visitor, nsFilter)
        }
        for (param in params.resolve()) {
            param.accept(visitor, nsFilter)
        }
        for (local in locals) {
            local.accept(visitor, nsFilter)
        }
    }

    override fun merge(element: MethodNode): MethodNode? {
        val merged = super.merge(element)
        // override to merge disperate constructor methods
        val name = names.values.first()
        if (merged == null && (name == "<init>" || name == "<clinit>") && name == element.names.values.first()) {

            // test desc
            if (descs.isNotEmpty() && element.descs.isNotEmpty()) {
                val descNs = descs.keys.first()
                return if (hasDescriptor()) {
                    if (element.hasDescriptor()) {
                        if (getMethodDesc(descNs) == element.getMethodDesc(descNs)) {
                            // merge
                            element.setNames(names)
                            doMerge(element)
                            element
                        } else {
                            // dont merge
                            null
                        }
                    } else {
                        // merge but also create new unmerged one
                        val new = create(parent as ClassNode)
                        new.setNames(names)
                        element.setNames(names)
                        doMerge(element)
                        doMerge(new)
                        new
                    }
                } else {
                    if (element.hasDescriptor()) {
                        // merge, but also create a new one without descs
                        val new = create(parent as ClassNode)
                        new.setNames(element.names)
                        new.setNames(names)
                        new.setDescriptors(descs)
                        element.setNames(names)
                        doMerge(element)
                        doMerge(new)
                        new
                    } else {
                        // merge
                        element.setNames(names)
                        doMerge(element)
                        element
                    }
                }
            }
        }
        return merged
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitMethod(EmptyClassVisitor(), names.mapValues { it.value to getMethodDesc(it.key) })
//        acceptInner(DelegateMethodVisitor(EmptyMethodVisitor(), delegator), root.namespaces)
    }

}