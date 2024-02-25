package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationElementName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.EnumConstant
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.PackageNode
import xyz.wagyourtail.unimined.mapping.util.maybeEscape
import xyz.wagyourtail.unimined.mapping.visitor.*

class MappingTree : BaseNode<MappingVisitor, NullVisitor>(null), MappingVisitor {
    private val _namespaces = mutableListOf<Namespace>()
    private val _packages = mutableSetOf<PackageNode>()
    private val _classes = mutableSetOf<ClassNode>()
    private val _constantGroups = mutableSetOf<ConstantGroupNode>()

    val namespaces: List<Namespace> get() = _namespaces
    val packages: Set<PackageNode> get() = _packages
    val classes: Set<ClassNode> get() = _classes
    val constantGroups: Set<ConstantGroupNode> get() = _constantGroups

    internal val byNamespace = mutableMapOf<Namespace, MutableMap<InternalName, ClassNode>>()

    fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return (byNamespace.getOrPut(namespace) {
            val map = mutableMapOf<InternalName, ClassNode>()
            _classes.forEach { c -> c.getName(namespace)?.let { map[it] = c } }
            map
        })[name]
    }

    internal fun mergeNs(names: Iterable<Namespace>) {
        for (ns in names) {
            if (ns !in _namespaces) {
                _namespaces.add(ns)
            }
        }
    }

    override fun nextUnnamedNs(): Namespace {
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
        return FieldOrMethodDescriptor.unchecked(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapDescriptor(fromNs: Namespace, toNs: Namespace, descriptor: FieldDescriptor): FieldDescriptor {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return descriptor
        return FieldDescriptor.unchecked(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapDescriptor(fromNs: Namespace, toNs: Namespace, descriptor: MethodDescriptor): MethodDescriptor {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return descriptor
        return MethodDescriptor.unchecked(buildString {
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

    fun mapAnnotation(fromNs: Namespace, toNs: Namespace, annotation: Annotation): Annotation {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return annotation
        return Annotation.unchecked(buildString {
            annotation.accept(annotationRemapAcceptor(fromNs, toNs, annotation))
        })
    }

    fun map(fromNs: Namespace, toNs: Namespace, internalName: InternalName): InternalName {
        map(fromNs, toNs, FullyQualifiedName(ObjectType(internalName), null)).let {
            return it.getParts().first.getInternalName()
        }
    }

    fun map(fromNs: Namespace, toNs: Namespace, fullyQualifiedName: FullyQualifiedName): FullyQualifiedName {
        if (!namespaces.contains(fromNs) || !namespaces.contains(toNs)) {
            throw IllegalArgumentException("Invalid namespace")
        }
        if (fromNs == toNs) return fullyQualifiedName
        val parts = fullyQualifiedName.getParts()
        val cls = getClass(fromNs, parts.first.getInternalName())
        val mappedCls = cls?.getName(toNs) ?: parts.first.getInternalName()

        if (parts.second != null) {
            val mParts = parts.second!!.getParts()
            val mappedName = if (mParts.second == null || mParts.second!!.isFieldDescriptor()) {
                val fd = cls?.getFields(fromNs, mParts.first.value, mParts.second?.getFieldDescriptor())
                if (fd != null) {
                    fd.first().getName(toNs) ?: mParts.first.value
                } else {
                    mParts.first.value
                }
            } else {
                val md = cls?.getMethods(fromNs, mParts.first.value, mParts.second?.getMethodDescriptor())
                if (md != null) {
                    md.first().getName(toNs) ?: mParts.first.value
                } else {
                    mParts.first.value
                }
            }
            val mappedDesc = if (mParts.second == null) {
                null
            } else {
                mapDescriptor(fromNs, toNs, mParts.second!!)
            }
            return FullyQualifiedName(ObjectType(mappedCls), NameAndDescriptor(UnqualifiedName.unchecked(mappedName), mappedDesc))
        }
        return FullyQualifiedName(ObjectType(mappedCls), null)
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

    fun StringBuilder.annotationRemapAcceptor(fromNs: Namespace, toNs: Namespace, annotation: Annotation): (Any, Boolean) -> Boolean {
        val ann = annotation.getParts().first
        val cls = getClass(fromNs, ann.getInternalName())
        return { obj, leaf ->
            when (obj) {
                is Annotation -> {
                    val mapped = mapAnnotation(fromNs, toNs, obj)
                    append(mapped)
                    false
                }
                is InternalName -> {
                    val mapped = getClass(fromNs, obj)?.getName(toNs)
                    if (mapped != null) {
                        append(mapped)
                    } else {
                        append(obj)
                    }
                    false
                }
                is AnnotationElementName -> {
                    if (cls != null) {
                        val md = cls.getMethods(fromNs, obj.unescape(), null).map { it.getName(toNs) }.toSet()
                        if (md.isNotEmpty()) {
                            val mappedName = md.first()!!
                            append(mappedName.maybeEscape())
                        } else {
                            append(obj)
                        }
                        false
                    } else {
                        append(obj)
                        false
                    }
                }
                is EnumConstant -> {
                    val parts = obj.getParts()
                    val first = parts.first
                    val eCls = getClass(fromNs, first.getInternalName())
                    val mapped = eCls?.getName(toNs)
                    if (mapped != null) {
                        append("L$mapped;")
                    } else {
                        append(first)
                    }
                    append(".")
                    val second = parts.second
                    val fd = eCls?.getFields(fromNs, second, null)?.map { it.getName(toNs) }?.toSet()
                    if (!fd.isNullOrEmpty()) {
                        val mappedName = fd.first()!!
                        append(mappedName.maybeEscape())
                    } else {
                        append(second)
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

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = packages.firstOrNull { it.names[ns] == names[ns] }
            if (existing != null) {
                // add other names
                existing.setNames(names)
                return existing
            }
        }
        val node = PackageNode(this)
        node.setNames(names)
        _packages.add(node)
        return node
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
        val node = ClassNode(this)
        node.setNames(names)
        _classes.add(node)
        return node
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val node = ConstantGroupNode(this, type, baseNs)
        node.addNamespaces(namespaces)
        _constantGroups.add(node)
        return node
    }

    fun accept(visitor: MappingVisitor, nsFilter: List<Namespace> = namespaces, minimize: Boolean = false) {
        acceptInner(visitor, nsFilter, minimize)
    }

    override fun acceptOuter(visitor: NullVisitor, nsFilter: List<Namespace>, minimize: Boolean): MappingVisitor? {
        return null
    }

    override fun acceptInner(visitor: MappingVisitor, nsFilter: List<Namespace>, minimize: Boolean) {
        visitor.visitHeader(*namespaces.map { it.name }.toTypedArray())
        super.acceptInner(visitor, nsFilter, minimize)
        for (pkg in packages) {
            pkg.accept(visitor, nsFilter, minimize)
        }
        for (cls in classes) {
            cls.accept(visitor, nsFilter, minimize)
        }
        for (group in constantGroups) {
            group.accept(visitor, nsFilter, minimize)
        }
    }
}