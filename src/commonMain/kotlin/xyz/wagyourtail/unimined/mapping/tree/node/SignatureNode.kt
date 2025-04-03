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
import xyz.wagyourtail.unimined.mapping.visitor.EmptySignatureParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptySignatureVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateSignatureVisitor

class SignatureNode<T: SignatureParentVisitor<T>>(parent: BaseNode<T, *>, val value: String, val baseNs: Namespace) : BaseNode<SignatureVisitor, T>(parent), SignatureVisitor {
    val _namespaces: MutableSet<Namespace> = mutableSetOf()
    val namespaces: Set<Namespace> get() = _namespaces

    fun addNamespaces(namespaces: Set<Namespace>) {
        root.mergeNs(namespaces)
        _namespaces.addAll(namespaces)
    }

    fun getClassSig(ns: Namespace): ClassSignature {
        return if (baseNs == ns) {
            ClassSignature.read(value)
        } else {
            ClassSignature.read(root.mapClassSignature(baseNs, ns, value))
        }
    }

    fun getMethodSig(ns: Namespace): MethodSignature {
        return if (baseNs == ns) {
            MethodSignature.read(value)
        } else {
            MethodSignature.read(root.mapMethodSignature(baseNs, ns, value))
        }
    }

    fun getFieldSig(ns: Namespace): FieldSignature {
        return if (baseNs == ns) {
            FieldSignature.read(value)
        } else {
            FieldSignature.read(root.mapFieldSignature(baseNs, ns, value))
        }
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): SignatureVisitor? {
        if (baseNs !in nsFilter) {
            val ns = nsFilter.filter { it in namespaces }.toSet()
            if (ns.isEmpty()) return null
            val first = ns.first()
            val sig = getSignature(first)
            return visitor.visitSignature(sig, first, ns - first)
        } else {
            return visitor.visitSignature(value, baseNs, nsFilter.filter { it in namespaces }.toSet() - baseNs)
        }
    }

    fun getSignature(toNs: Namespace): String {
        return when (parent) {
            is ClassNode -> parent.root.mapClassSignature(baseNs, toNs, value)
            is FieldNode -> parent.root.mapFieldSignature(baseNs, toNs, value)
            is MethodNode -> parent.root.mapMethodSignature(baseNs, toNs, value)
            is WildcardNode -> when (parent.type) {
                WildcardNode.WildcardType.METHOD -> parent.root.mapMethodSignature(baseNs, toNs, value)
                WildcardNode.WildcardType.FIELD -> parent.root.mapFieldSignature(baseNs, toNs, value)
            }

            else -> throw IllegalStateException("Invalid parent type")
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitSignature(EmptySignatureParentVisitor(), value, baseNs, namespaces)
        if (inner) acceptInner(DelegateSignatureVisitor(EmptySignatureVisitor(), delegator), root.namespaces)
    }

}