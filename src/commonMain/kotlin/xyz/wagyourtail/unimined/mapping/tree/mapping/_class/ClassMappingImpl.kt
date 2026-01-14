package xyz.wagyourtail.unimined.mapping.tree.mapping._class

import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.mapping.InterfaceMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.mapping.SealMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.SignatureMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.FieldMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.MemberMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.MethodMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.WildcardMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateClassMappingVisitor
import kotlin.collections.mapValues

class ClassMappingImpl(parent: AbstractMappingTree) : MemberMappingImpl<ClassMappingVisitor, RootMappingVisitor>(parent), ClassMapping, ClassMappingVisitor {
    private val _names = mutableMapOf<Namespace, InternalName>()
    private val _signatures = mutableListOf<SignatureMappingImpl<ClassMappingVisitor, ClassSignature>>()
    private val _innerClasses = mutableMapOf<InnerType, InnerClassMappingImpl>()
    private val _interfaces = mutableListOf<InterfaceMappingImpl>()
    private val _seals = mutableListOf<SealMappingImpl>()

    override val signatures: List<SignatureMappingImpl<ClassMappingVisitor, ClassSignature>> get() = _signatures

    override val names: Map<Namespace, InternalName> get() = _names

    val _wildcards = LazyResolvables<WildcardMappingVisitor, WildcardMappingImpl>(parent)
    val _fields = LazyResolvables<FieldMappingVisitor, FieldMappingImpl>(parent)
    val _methods = LazyResolvables<MethodMappingVisitor, MethodMappingImpl>(parent)

    override val wildcards: List<WildcardMappingImpl> get() = _wildcards.resolve()
    override val fields: List<FieldMappingImpl> get() = _fields.resolve()
    override val methods: List<MethodMappingImpl> get() = _methods.resolve()

    override val innerClasses: List<InnerClassMappingImpl> get() = _innerClasses.values.toList()

    override val interfaces: List<InterfaceMappingImpl> get() = _interfaces

    override val seals: List<SealMappingImpl> get() = _seals

    fun getName(namespace: Namespace) = _names[namespace]

    fun setNames(names: Map<Namespace, InternalName>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    /**
     *  because of how resolve works, there should really be a max of 2
     *  the one that matches best will be first.
     *  ie, if desc is not-null, it'll put the match with a not-null desc first.
     *  or vis-versa for null descs.
     */
    fun getFields(namespace: Namespace, name: UnqualifiedName, desc: FieldDescriptor?): List<FieldMappingImpl> {
        val fields = mutableListOf<FieldMappingImpl>()
        for (field in this.fields) {
            if (field.getName(namespace) == name) {
                if (desc == null || !field.hasDescriptor() || field.getFieldDesc(namespace) == desc) {
                    // ensure best match first
                    if ((desc == null) xor field.hasDescriptor()) {
                        fields.add(0, field)
                    } else {
                        fields.add(field)
                    }
                }
            }
        }
        return fields
    }

    /**
     *  because of how resolve works, there should really be a max of 2
     *  the one that matches best will be first.
     *  ie, if desc is not-null, it'll put the match with a not-null desc first.
     *  or vis-versa for null descs.
     */
    fun getMethods(namespace: Namespace, name: UnqualifiedName, desc: MethodDescriptor?): List<MethodMappingImpl> {
        val methods = mutableListOf<MethodMappingImpl>()
        for (method in this.methods) {
            if (method.getName(namespace) == name) {
                if (desc == null || !method.hasDescriptor() || method.getMethodDesc(namespace) == desc) {
                    // ensure best match first
                    if ((desc == null) xor method.hasDescriptor()) {
                        methods.add(0, method)
                    } else {
                        methods.add(method)
                    }
                }
            }
        }
        return methods
    }

    fun getWildcards(type: WildcardType, namespace: Namespace, desc: FieldOrMethodDescriptor?): List<WildcardMappingImpl> {
        val wildcards = mutableListOf<WildcardMappingImpl>()
        for (wildcard in this.wildcards) {
            if (wildcard.type == type) {
                if (desc == null || wildcard.hasDescriptor() || wildcard.getDescriptor(namespace) == desc) {
                    // ensure best match first
                    if ((desc == null) xor wildcard.hasDescriptor()) {
                        wildcards.add(0, wildcard)
                    } else {
                        wildcards.add(wildcard)
                    }
                }
            }
        }
        return wildcards
    }

    override fun visitSignature(value: ClassSignature, baseNs: Namespace): SignatureMappingVisitor {
        val node = SignatureMappingImpl(this, value, baseNs)
        _signatures.add(node)
        return node
    }

    override fun visitMethod(namespaces: Map<Namespace, MethodNameAndDescriptor>) = MethodMappingImpl(this).apply {
        setNames(namespaces.mapValues { it.value.name })
        setMethodDescs(namespaces.mapNotNullValues { it.value.descriptor })
        _methods.addUnresolved(this)
    }

    override fun visitField(namespaces: Map<Namespace, FieldNameAndDescriptor>) = FieldMappingImpl(this).apply {
        setNames(namespaces.mapValues { it.value.name })
        setFieldDescs(namespaces.mapNotNullValues { it.value.descriptor })
        _fields.addUnresolved(this)
    }

    override fun visitWildcard(
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ) = WildcardMappingImpl(this, type, descs).apply {
        _wildcards.addUnresolved(this)
    }

    override fun visitSeal(type: SealedType, name: InternalName?, baseNs: Namespace): SealMappingVisitor? {
        val seal = SealMappingImpl(this, type, name, baseNs)
        _seals.add(seal)
        return seal
    }

    override fun visitInterface(
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
    ): InterfaceMappingVisitor? {
        val intf = InterfaceMappingImpl(this, type, name, baseNs)
        _interfaces.add(intf)
        return intf
    }

    override fun visitInnerClass(
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor {
        // find existing
        val inner = if (type in _innerClasses) {
            _innerClasses.getValue(type)
        } else {
            InnerClassMappingImpl(this, type).also { _innerClasses[type] = it }
        }
        inner.setNames(names)
        return inner
    }


    override fun acceptOuter(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>): ClassMappingVisitor? {
        val names = _names.filterNotNullValues().filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitClass(names)
    }

    override fun acceptInner(visitor: ClassMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        super.acceptInner(visitor, nsFilter, sort)
        for (signature in if (sort) signatures.sortedBy { it.toString() } else signatures) {
            signature.accept(visitor, nsFilter, sort)
        }
        for (inner in if (sort) innerClasses.sortedBy { it.toString() } else innerClasses) {
            inner.accept(visitor, nsFilter, sort)
        }
        for (seal in if (sort) seals.sortedBy { it.toString() } else seals) {
            seal.accept(visitor, nsFilter, sort)
        }
        for (intf in if (sort) interfaces.sortedBy { it.toString() } else interfaces) {
            intf.accept(visitor, nsFilter, sort)
        }
        for (wildcard in if (sort) wildcards.sortedBy { it.toString() } else wildcards) {
            wildcard.accept(visitor, nsFilter, sort)
        }
        for (field in if (sort) fields.sortedBy { it.toString() } else fields) {
            field.accept(visitor, nsFilter, sort)
        }
        for (method in if (sort) methods.sortedBy { it.toString() } else methods) {
            method.accept(visitor, nsFilter, sort)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces.toList()
        delegator.visitClass(EmptyRootMappingVisitor(), names.filterNotNullValues())
        if (inner) acceptInner(DelegateClassMappingVisitor(EmptyClassMappingVisitor(), delegator), root.namespaces, true)
    }

}