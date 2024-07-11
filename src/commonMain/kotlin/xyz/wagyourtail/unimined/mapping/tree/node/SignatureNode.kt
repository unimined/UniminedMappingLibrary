package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.FieldNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateCommentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateSignatureVisitor

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

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): SignatureVisitor? {
        return visitor.visitSignature(nsFilter.associateWith { getSignature(it) })
    }

    fun getSignature(toNs: Namespace): String {
        val fromNs = names.keys.first()
        val sig = names.getValue(fromNs)
        return when (parent) {
            is ClassNode -> parent.root.mapClassSignature(fromNs, toNs, sig)
            is FieldNode -> parent.root.mapFieldSignature(fromNs, toNs, sig)
            is MethodNode -> parent.root.mapMethodSignature(fromNs, toNs, sig)
            is WildcardNode -> when (parent.type) {
                WildcardNode.WildcardType.METHOD -> parent.root.mapMethodSignature(fromNs, toNs, sig)
                WildcardNode.WildcardType.FIELD -> parent.root.mapFieldSignature(fromNs, toNs, sig)
            }

            else -> throw IllegalStateException("Invalid parent type")
        }
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitSignature(EmptySignatureParentVisitor(), names)
//        acceptInner(DelegateSignatureVisitor(EmptySignatureVisitor(), delegator), root.namespaces)
    }

}