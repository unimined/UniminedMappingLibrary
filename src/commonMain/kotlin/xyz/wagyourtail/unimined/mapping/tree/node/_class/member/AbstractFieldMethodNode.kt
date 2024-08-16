package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MemberVisitor

abstract class AbstractFieldMethodNode<T: MemberVisitor<T>>(parent: ClassNode) : MemberNode<T, ClassVisitor>(parent) {

    private val _names = mutableMapOf<Namespace, String>()
    private val _descs = mutableMapOf<Namespace, FieldOrMethodDescriptor>()
    val names: Map<Namespace, String> get() = _names
    val descs: Map<Namespace, FieldOrMethodDescriptor> get() = _descs

    fun hasDescriptor() = descs.isNotEmpty()

    fun getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return root.mapDescriptor(fromNs, namespace, descs[fromNs]!!)
    }

    fun getName(namespace: Namespace) = names[namespace]

    open fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>) {
        root.mergeNs(descs.keys)
        this._descs.putAll(descs)
    }

}