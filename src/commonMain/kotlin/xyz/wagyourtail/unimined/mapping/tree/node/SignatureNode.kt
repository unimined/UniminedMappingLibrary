package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.visitor.SignatureParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureVisitor

class SignatureNode<T: SignatureParentVisitor<T>>(parent: BaseNode<T, *>) : BaseNode<SignatureVisitor, T>(parent), SignatureVisitor {
    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    val names: Map<Namespace, String> get() = _names

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    fun getClassSig(ns: Namespace): ClassSignature? {
        if (names.isEmpty()) return null
        if (ns in names) {
            return ClassSignature.read(names[ns]!!)
        }
        val fromNs = names.keys.first()
        return ClassSignature.read(root.mapClassSignature(fromNs, ns, names[fromNs]!!))
    }

    fun getMethodSig(ns: Namespace): MethodSignature? {
        if (names.isEmpty()) return null
        if (ns in names) {
            return MethodSignature.read(names[ns]!!)
        }
        val fromNs = names.keys.first()
        return MethodSignature.read(root.mapMethodSignature(fromNs, ns, names[fromNs]!!))
    }

    fun getFieldSig(ns: Namespace): FieldSignature? {
        if (names.isEmpty()) return null
        if (ns in names) {
            return FieldSignature.read(names[ns]!!)
        }
        val fromNs = names.keys.first()
        return FieldSignature.read(root.mapFieldSignature(fromNs, ns, names[fromNs]!!))
    }

    override fun acceptOuter(visitor: T, minimize: Boolean): SignatureVisitor? {
        return visitor.visitSignature(names)
    }

}