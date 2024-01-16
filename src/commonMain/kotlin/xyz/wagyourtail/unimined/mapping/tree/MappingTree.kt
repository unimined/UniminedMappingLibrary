package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.*

class MappingTree : BaseNode<MappingVisitor, NullVisitor>(null), MappingVisitor {
    private val _namespaces = mutableListOf<Namespace>()
    private val classes = mutableSetOf<ClassNode>()
    private val constantGroups = mutableSetOf<ConstantGroupNode>()

    val namespaces: List<Namespace> get() = _namespaces

    internal val byNamespace = mutableMapOf<Namespace, MutableMap<InternalName, ClassNode>>().withDefault { ns ->
        val map = mutableMapOf<InternalName, ClassNode>()
        classes.forEach { c -> c.getName(ns)?.let { map[it] = c } }
        map
    }

    fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return byNamespace.getValue(namespace)[name]
    }

    internal fun mergeNs(names: Iterable<Namespace>) {
        names.filter { it !in namespaces }.forEach { _namespaces.add(it) }
    }

    fun nextUnnamedNs(): Namespace {
        var i = 0
        while (true) {
            val ns = Namespace("unnamed_$i")
            if (ns !in namespaces) {
                _namespaces.add(ns)
                return ns
            }
            i++
        }
    }

    fun mapDescriptor(fromNs: Namespace, toNs: Namespace, descriptor: FieldOrMethodDescriptor): FieldOrMethodDescriptor {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return descriptor
        return FieldOrMethodDescriptor.read(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapDescriptor(fromNs: Namespace, toNs: Namespace, descriptor: FieldDescriptor): FieldDescriptor {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return descriptor
        return FieldDescriptor.read(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapDescriptor(fromNs: Namespace, toNs: Namespace, descriptor: MethodDescriptor): MethodDescriptor {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return descriptor
        return MethodDescriptor.read(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapClassSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseClassSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    fun mapMethodSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseMethodSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    fun mapFieldSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseFieldSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    // TODO: test
    fun StringBuilder.descRemapAcceptor(fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is InternalName -> {
                    val mapped = getClass(fromNs, obj)?.getName(toNs)
                    if (mapped != null) {
                        append(mapped)
                    } else {
                        append(obj)
                    }
                    false
                }
                else -> {
                    if (leaf) {
                        append(obj.toString())
                    }
                    true
                }
            }
        }
    }

    //TODO: test
    fun StringBuilder.signatureRemapAcceptor(fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is InternalName -> {
                    val mapped = getClass(fromNs, obj)?.getName(toNs)
                    if (mapped != null) {
                        append(mapped)
                    } else {
                        append(obj)
                    }
                    false
                }
                is ClassTypeSignature -> {
                    val clsNameBuilder = StringBuilder()
                    val (pkg, cls, sufs) = obj.getParts()
                    if (pkg != null) {
                        clsNameBuilder.append(pkg)
                    }
                    val (name, types) = cls.getParts()
                    clsNameBuilder.append(name)

                    val mappedBuilder = StringBuilder()
                    val mappedOuter = getClass(fromNs, InternalName.read(clsNameBuilder.toString()))?.getName(toNs) ?: clsNameBuilder.toString()
                    mappedBuilder.append(mappedOuter)
                    append(mappedOuter)
                    types?.accept(signatureRemapAcceptor(fromNs, toNs))
                    for (suf in sufs) {
                        val (innerName, innerTypes) = suf.getParts().getParts()
                        clsNameBuilder.append("$").append(innerName)
                        val mappedInner = getClass(fromNs, InternalName.read(clsNameBuilder.toString()))?.getName(toNs)?.toString() ?: clsNameBuilder.toString()
                        mappedBuilder.append("$")
                        val innerNameMapped = mappedInner.substring(mappedBuilder.length)
                        mappedBuilder.append(innerNameMapped)
                        append(".")
                        append(innerNameMapped)
                        innerTypes?.accept(signatureRemapAcceptor(fromNs, toNs))
                    }
                    false
                }
                else -> {
                    if (leaf) {
                        append(obj.toString())
                    }
                    true
                }
            }
        }
    }

    override fun visitHeader(vararg namespaces: String) {
        mergeNs(namespaces.map { Namespace(it) }.toSet())
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassNode {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = getClass(ns, names[ns]!!)
            if (existing != null) {
                // add other names
                existing.setNames(names)
                return existing
            }
        }
        names.keys.filter { it !in namespaces }.forEach { _namespaces.add(it) }
        val node = ClassNode(this)
        node.setNames(names)
        classes.add(node)
        return node
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val node = ConstantGroupNode(this, type, baseNs)
        node.addNamespaces(namespaces)
        constantGroups.add(node)
        return node
    }

    fun accept(visitor: MappingVisitor, minimize: Boolean = false) {
        acceptInner(visitor, minimize)
    }

    override fun acceptOuter(visitor: NullVisitor, minimize: Boolean): MappingVisitor? {
        return null
    }

    override fun acceptInner(visitor: MappingVisitor, minimize: Boolean) {
        visitor.visitHeader(*namespaces.map { it.name }.toTypedArray())
        super.acceptInner(visitor, minimize)
        for (cls in classes) {
            cls.accept(visitor, minimize)
        }
        for (group in constantGroups) {
            group.accept(visitor, minimize)
        }
    }
}