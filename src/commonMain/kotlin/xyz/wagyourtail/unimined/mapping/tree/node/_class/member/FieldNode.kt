package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.node.SignatureNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureVisitor

class FieldNode(parent: ClassNode): FieldMethodResolvable<FieldNode, FieldVisitor>(parent, ::FieldNode), FieldVisitor {
    private val _signatures = mutableSetOf<SignatureNode<FieldVisitor>>()

    val signatures: Set<SignatureNode<FieldVisitor>> get() = _signatures

    fun getFieldDesc(namespace: Namespace) = getDescriptor(namespace)?.getFieldDescriptor()

    fun setFieldDescs(descs: Map<Namespace, FieldDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldOrMethodDescriptor.unchecked(it.value.toString()) })
    }


    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor {
        val node = SignatureNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _signatures.add(node)
        return node
    }

    override fun acceptOuter(visitor: ClassVisitor, nsFilter: Collection<Namespace>): FieldVisitor? {
        val names = names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let { name to getFieldDesc(ns) } }
        if (names.isEmpty()) return null
        return visitor.visitField(names)
    }

    override fun acceptInner(visitor: FieldVisitor, nsFilter: Collection<Namespace>) {
        super.acceptInner(visitor, nsFilter)
        for (signature in _signatures) {
            signature.accept(visitor, nsFilter)
        }
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitField(EmptyClassVisitor(), names.mapValues { it.value to getFieldDesc(it.key) })
//        acceptInner(DelegateFieldVisitor(EmptyFieldVisitor(), delegator), root.namespaces)
    }

}