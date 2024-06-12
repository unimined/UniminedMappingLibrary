package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ExceptionNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.LocalNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.method.ParameterNode
import xyz.wagyourtail.unimined.mapping.visitor.*

class MethodNode(parent: ClassNode) : FieldMethodResolvable<MethodNode, MethodVisitor, MethodVisitor>(parent, ::MethodNode), MethodVisitor {
    private val _params: MutableList<ParameterNode<MethodVisitor>> = mutableListOf()
    private val _locals: MutableList<LocalNode<MethodVisitor>> = mutableListOf()
    private val _exceptions: MutableList<ExceptionNode<MethodVisitor>> = mutableListOf()

    val params: List<ParameterNode<MethodVisitor>> get() = _params
    val locals: List<LocalNode<MethodVisitor>> get() = _locals
    val exceptions: List<ExceptionNode<MethodVisitor>> get() = _exceptions


    fun getMethodDesc(namespace: Namespace) = getDescriptor(namespace)?.getMethodDescriptor()

    fun setMethodDescs(descs: Map<Namespace, MethodDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldOrMethodDescriptor.unchecked(it.value.toString()) })
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

    override fun acceptOuter(visitor: ClassVisitor, minimize: Boolean): MethodVisitor? {
        val names = if (minimize) {
            val descNs = root.namespaces.firstOrNull { it in names }
            if (descNs != null) {
                names.mapValues { (ns, name) -> name to if (ns == descNs) getMethodDesc(ns) else null }
            } else {
                names.mapValues { (_, name) -> name to null }
            }
        } else {
            names.mapValues { (ns, name) -> name.let { name to descs[ns]?.getMethodDescriptor() } }
        }
        if (names.isEmpty()) return null
        return visitor.visitMethod(names)
    }

    override fun acceptInner(visitor: MethodVisitor, minimize: Boolean) {
        super.acceptInner(visitor, minimize)
        for (exception in exceptions) {
            exception.accept(visitor, minimize)
        }
        for (param in params) {
            param.accept(visitor, minimize)
        }
        for (local in locals) {
            local.accept(visitor, minimize)
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
}