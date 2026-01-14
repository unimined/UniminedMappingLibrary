package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.mapping.SignatureMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyFieldMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldMapping
import xyz.wagyourtail.unimined.mapping.visitor.FieldMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.SignatureMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateFieldMappingVisitor

class FieldMappingImpl(parent: ClassMappingImpl): FieldMethodResolvable<FieldMappingImpl, FieldMappingVisitor, FieldDescriptor>(parent, ::FieldMappingImpl), FieldMapping, FieldMappingVisitor {

    private val _signatures = mutableListOf<SignatureMappingImpl<FieldMappingVisitor, FieldSignature>>()

    override val signatures: List<SignatureMappingImpl<FieldMappingVisitor, FieldSignature>> get() = _signatures

    fun getFieldDesc(namespace: Namespace) = getDescriptor(namespace)?.getFieldDescriptor()

    fun setFieldDescs(descs: Map<Namespace, FieldDescriptor>) {
        root.mergeNs(descs.keys)
        setDescriptors(descs.mapValues { FieldDescriptor.unchecked(it.value.toString()) })
    }


    override fun visitSignature(value: FieldSignature, baseNs: Namespace): SignatureMappingVisitor {
        val node = SignatureMappingImpl(this, value, baseNs)
        _signatures.add(node)
        return node
    }

    override fun acceptOuter(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>): FieldMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }.mapValues { (ns, name) -> name.let { FieldNameAndDescriptor(name, getFieldDesc(ns)) } }
        if (names.isEmpty()) return null
        return visitor.visitField(names)
    }

    override fun acceptInner(visitor: FieldMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (signature in _signatures.sortedBy { it.toString() }) {
            signature.accept(visitor, nsFilter, sort)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitField(EmptyClassMappingVisitor(), names.mapValues { FieldNameAndDescriptor(it.value, getFieldDesc(it.key)) })
        if (inner) acceptInner(DelegateFieldMappingVisitor(EmptyFieldMappingVisitor(), delegator), root.namespaces, true)
    }

}