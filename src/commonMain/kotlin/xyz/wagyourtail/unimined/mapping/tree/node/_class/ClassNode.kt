package xyz.wagyourtail.unimined.mapping.tree.node._class

import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.InterfaceNode
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.node.SealNode
import xyz.wagyourtail.unimined.mapping.tree.node.SignatureNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.FieldNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.*

class ClassNode(parent: AbstractMappingTree) : MemberNode<ClassVisitor, MappingVisitor>(parent), ClassVisitor {
    private val _names = mutableMapOf<Namespace, InternalName?>()
    private val _signatures = mutableSetOf<SignatureNode<ClassVisitor>>()
    private val _inners = mutableMapOf<InnerClassNode.InnerType, InnerClassNode>()
    private val _interfaces = mutableSetOf<InterfaceNode>()
    private val _seals = mutableSetOf<SealNode>()

    val signatures: Set<SignatureNode<ClassVisitor>> get() = _signatures

    val names: Map<Namespace, InternalName?> get() = _names

    val wildcards = LazyResolvables<WildcardVisitor, WildcardNode>(parent) {
        WildcardNode(this, it.type, it.descs)
    }
    val fields = LazyResolvables<FieldVisitor, FieldNode>(parent) {
        FieldNode(this)
    }
    val methods = LazyResolvables<MethodVisitor, MethodNode>(parent) {
        MethodNode(this)
    }

    val inners: Map<InnerClassNode.InnerType, InnerClassNode> get() = _inners

    val interfaces: Set<InterfaceNode> get() = _interfaces

    val seals: Set<SealNode> get() = _seals

    fun getName(namespace: Namespace) = _names[namespace]

    fun setNames(names: Map<Namespace, InternalName?>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    /**
     *  because of how resolve works, there should really be a max of 2
     *  the one that matches best will be first.
     *  ie, if desc is not-null, it'll put the match with a not-null desc first.
     *  or vis-versa for null descs.
     */
    fun getFields(namespace: Namespace, name: String, desc: FieldDescriptor?): List<FieldNode> {
        val fields = mutableListOf<FieldNode>()
        for (field in this.fields.resolve()) {
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
    fun getMethods(namespace: Namespace, name: String, desc: MethodDescriptor?): List<MethodNode> {
        val methods = mutableListOf<MethodNode>()
        for (method in this.methods.resolve()) {
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

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor {
        val node = SignatureNode(this, value, baseNs)
        node.addNamespaces(namespaces)
        _signatures.add(node)
        return node
    }

    override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>) = MethodNode(this).apply {
        setNames(namespaces.mapValues { it.value.first })
        setMethodDescs(namespaces.mapNotNullValues { it.value.second })
        methods.addUnresolved(this)
    }

    override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>) = FieldNode(this).apply {
        setNames(namespaces.mapValues { it.value.first })
        setFieldDescs(namespaces.mapNotNullValues { it.value.second })
        fields.addUnresolved(this)
    }

    override fun visitWildcard(
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ) = WildcardNode(this, type, descs).apply {
        wildcards.addUnresolved(this)
    }

    override fun visitSeal(type: SealedType, name: InternalName?, baseNs: Namespace, namespaces: Set<Namespace>): SealVisitor? {
        val seal = SealNode(this, type, name, baseNs)
        seal.addNamespaces(namespaces)
        _seals.add(seal)
        return seal
    }

    override fun visitInterface(
        type: InterfacesType,
        name: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): InterfaceVisitor? {
        val intf = InterfaceNode(this, type, name, baseNs)
        intf.addNamespaces(namespaces)
        _interfaces.add(intf)
        return intf
    }

    override fun visitInnerClass(
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor {
        // find existing
        val inner = if (type in inners) {
            inners.getValue(type)
        } else {
            InnerClassNode(this, type).also { _inners[type] = it }
        }
        inner.setNames(names.mapValues { it.value.first })
        inner.setTargets(names.mapNotNullValues { it.value.second })
        return inner
    }


    override fun acceptOuter(visitor: MappingVisitor, nsFilter: Collection<Namespace>): ClassVisitor? {
        val names = _names.filterNotNullValues().filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitClass(names)
    }

    override fun acceptInner(visitor: ClassVisitor, nsFilter: Collection<Namespace>) {
        super.acceptInner(visitor, nsFilter)
        for (signature in signatures) {
            signature.accept(visitor, nsFilter)
        }
        for (inner in inners.values) {
            inner.accept(visitor, nsFilter)
        }
        for (seal in seals) {
            seal.accept(visitor, nsFilter)
        }
        for (intf in interfaces) {
            intf.accept(visitor, nsFilter)
        }
        for (wildcard in wildcards.resolve()) {
            wildcard.accept(visitor, nsFilter)
        }
        for (field in fields.resolve()) {
            field.accept(visitor, nsFilter)
        }
        for (method in methods.resolve()) {
            method.accept(visitor, nsFilter)
        }
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitClass(EmptyMappingVisitor(), names.filterNotNullValues())
//        acceptInner(DelegateClassVisitor(EmptyClassVisitor(), delegator), root.namespaces)
    }

}