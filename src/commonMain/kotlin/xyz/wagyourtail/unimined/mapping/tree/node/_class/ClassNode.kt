package xyz.wagyourtail.unimined.mapping.tree.node._class

import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.FieldNode
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvables
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MethodNode
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.util.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.*

class ClassNode(parent: AbstractMappingTree) : MemberNode<ClassVisitor, ClassVisitor, MappingVisitor>(parent), ClassVisitor {
    private val _names: MutableMap<Namespace, InternalName?> = mutableMapOf()

    val names: Map<Namespace, InternalName?> get() = _names

    val fields = LazyResolvables(parent) {
        FieldNode(this)
    }
    val methods = LazyResolvables(parent) {
        MethodNode(this)
    }
    val inners = mutableSetOf<InnerClassNode>()

    fun getName(namespace: Namespace) = _names[namespace]

    fun setNames(names: Map<Namespace, InternalName?>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    fun getFields(namespace: Namespace, name: String, desc: FieldDescriptor?): Set<FieldNode> {
        val fields = mutableSetOf<FieldNode>()
        for (field in this.fields.resolve()) {
            if (field.getName(namespace) == name) {
                if (desc == null || !field.hasDescriptor() || field.getFieldDesc(namespace) == desc) {
                    fields.add(field)
                }
            }
        }
        return fields
    }

    fun getMethods(namespace: Namespace, name: String, desc: MethodDescriptor?): Set<MethodNode> {
        val methods = mutableSetOf<MethodNode>()
        for (method in this.methods.resolve()) {
            if (method.getName(namespace) == name) {
                if (desc == null || !method.hasDescriptor() || method.getMethodDesc(namespace) == desc) {
                    methods.add(method)
                }
            }
        }
        return methods
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

    override fun visitInnerClass(
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        // find existing
        for (inner in inners) {
            if (inner.names.keys.intersect(names.keys).isNotEmpty()) {
                inner.setNames(names.mapValues { it.value.first })
                inner.setTargets(names.mapNotNullValues { it.value.second })
                return inner
            }
        }
        val inner = InnerClassNode(this, type)
        inner.setNames(names.mapValues { it.value.first })
        inner.setTargets(names.mapNotNullValues { it.value.second })
        inners.add(inner)
        return inner
    }

    override fun acceptOuter(visitor: MappingVisitor,minimize: Boolean): ClassVisitor? {
        return visitor.visitClass(_names.filterNotNullValues())
    }

    override fun acceptInner(visitor: ClassVisitor, minimize: Boolean) {
        super.acceptInner(visitor, minimize)
        for (inner in inners) {
            inner.accept(visitor, minimize)
        }
        for (field in fields.resolve()) {
            field.accept(visitor, minimize)
        }
        for (method in methods.resolve()) {
            method.accept(visitor, minimize)
        }
    }

}