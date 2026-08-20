package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MemberMappingVisitor

abstract class AbstractFieldMethodMappingImpl<T: MemberMappingVisitor, U: FieldOrMethodDescriptor>(parent: ClassMappingImpl) : MemberMappingImpl<T, ClassMappingVisitor>(parent) {

    private val _names = mutableMapOf<Namespace, UnqualifiedName>()
    private val _descs = mutableMapOf<Namespace, U>()
    val names: Map<Namespace, UnqualifiedName> get() = _names
    val descs: Map<Namespace, U> get() = _descs

    fun getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return root.map(fromNs, namespace, descs[fromNs]!!)
    }

    fun getName(namespace: Namespace) = names[namespace]

    open fun setNames(names: Map<Namespace, UnqualifiedName>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    fun setDescriptors(descs: Map<Namespace, U>) {
        root.mergeNs(descs.keys)
        this._descs.putAll(descs)
    }

}