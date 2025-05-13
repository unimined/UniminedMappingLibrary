package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.commonskt.utils.maybeEscape
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.csrg.CsrgReader.mapPackage
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationElementName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationIdentifier
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.EnumConstant
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.NullVisitor
import kotlin.js.JsName
import kotlin.jvm.JvmName

abstract class AbstractMappingTree : BaseNode<MappingVisitor, NullVisitor>(null), MappingVisitor {
    private val _namespaces = mutableListOf<Namespace>()
    val namespaces: List<Namespace> get() = _namespaces

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

    abstract fun getClass(namespace: Namespace, name: InternalName): ClassNode?

    fun visitClass(vararg names: Pair<Namespace, InternalName>) = visitClass(names.toMap())

    fun checkNamespace(ns: Namespace) {
        if (ns !in namespaces) {
            throw IllegalArgumentException("Invalid namespace $ns, expected one of: $namespaces")
        }
    }

    fun map(fromNs: Namespace, toNs: Namespace, descriptor: FieldOrMethodDescriptor): FieldOrMethodDescriptor {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return descriptor
        return FieldOrMethodDescriptor.unchecked(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun map(fromNs: Namespace, toNs: Namespace, descriptor: FieldDescriptor): FieldDescriptor {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return descriptor
        return FieldDescriptor.unchecked(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun map(fromNs: Namespace, toNs: Namespace, descriptor: MethodDescriptor): MethodDescriptor {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return descriptor
        return MethodDescriptor.unchecked(buildString {
            descriptor.accept(descRemapAcceptor(fromNs, toNs))
        })
    }

    fun mapClassSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseClassSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    fun mapMethodSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseMethodSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    fun mapFieldSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return buildString {
            JVMS.parseFieldSignature(signature).accept(signatureRemapAcceptor(fromNs, toNs))
        }
    }

    fun mapAnnotation(fromNs: Namespace, toNs: Namespace, annotation: Annotation): Annotation {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return annotation
        return Annotation.unchecked(buildString {
            annotation.accept(annotationRemapAcceptor(fromNs, toNs, annotation))
        })
    }

    open fun map(fromNs: Namespace, toNs: Namespace, internalName: InternalName): InternalName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return internalName
        val cls = getClass(fromNs, internalName)
        if (cls != null) {
            return cls.getName(toNs) ?: internalName
        }
        val parts = internalName.getParts()
        val pkg = map(fromNs, toNs, parts.first)
        return InternalName(pkg, parts.second)
    }

    open fun map(fromNs: Namespace, toNs: Namespace, packageName: PackageName): PackageName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return packageName
        for ((map, _) in packagesIter()) {
            if (map.values.contains(packageName)) {
                return map[toNs] ?: packageName
            }
        }
        return packageName
    }

    fun map(fromNs: Namespace, toNs: Namespace, fullyQualifiedName: FullyQualifiedName): FullyQualifiedName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return fullyQualifiedName
        val parts = fullyQualifiedName.getParts()
        val cls = getClass(fromNs, parts.first.getInternalName())
        if (cls == null) {
            val objParts = parts.first.getInternalName().getParts()
            val pkg = map(fromNs, toNs, objParts.first)
            return FullyQualifiedName(ObjectType(InternalName(pkg, objParts.second)), parts.second)
        }
        val mappedCls = cls.getName(toNs) ?: parts.first.getInternalName()

        if (parts.second != null) {
            val mParts = parts.second!!.getParts()
            val mappedName = if (mParts.second == null || mParts.second!!.isFieldDescriptor()) {
                val fd = cls.getFields(fromNs, mParts.first.value, mParts.second?.getFieldDescriptor())
                fd.firstOrNull()?.getName(toNs) ?: mParts.first.value
            } else {
                val md = cls.getMethods(fromNs, mParts.first.value, mParts.second?.getMethodDescriptor())
                md.firstOrNull()?.getName(toNs) ?: mParts.first.value
            }
            val mappedDesc = if (mParts.second == null) {
                null
            } else {
                map(fromNs, toNs, mParts.second!!)
            }
            return FullyQualifiedName(ObjectType(mappedCls), NameAndDescriptor(UnqualifiedName.unchecked(mappedName), mappedDesc))
        }
        return FullyQualifiedName(ObjectType(mappedCls), null)
    }

    @JsName("mapObjectType")
    @JvmName("mapObjectType")
    fun map(fromNs: Namespace, toNs: Namespace, objectType: ObjectType): ObjectType {
        return ObjectType(map(fromNs, toNs, objectType.getInternalName()))
    }

    // TODO: test
    private fun StringBuilder.descRemapAcceptor(fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is InternalName -> {
                    append(map(fromNs, toNs, obj))
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
    private fun StringBuilder.signatureRemapAcceptor(fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
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
                    val mappedOuter = map(fromNs, toNs, InternalName.read(clsNameBuilder.toString()))
                    mappedBuilder.append(mappedOuter)
                    append(mappedOuter)
                    types?.accept(signatureRemapAcceptor(fromNs, toNs))
                    for (suf in sufs) {
                        val (innerName, innerTypes) = suf.getParts().getParts()
                        clsNameBuilder.append("$").append(innerName)
                        val mappedInner = map(fromNs, toNs, InternalName.read(clsNameBuilder.toString()))
                        mappedBuilder.append("$")
                        val innerNameMapped = mappedInner.toString().substring(mappedBuilder.length)
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

    private fun StringBuilder.annotationRemapAcceptor(fromNs: Namespace, toNs: Namespace, annotation: Annotation): (Any, Boolean) -> Boolean {
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
                    val mapped = map(fromNs,  toNs, obj)
                    append(mapped)
                    false
                }
                is AnnotationElementName -> {
                    if (cls != null) {
                        val md = cls.getMethods(fromNs, obj.value.unescape(), null).map { it.getName(toNs) }
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
                else -> {
                    if (leaf) {
                        append(obj.toString())
                    }
                    true
                }
            }
        }
    }

    open fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassNode>> {
        return classList().asSequence().map { (names, cls, _) -> names to cls }.iterator()
    }

    open fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageNode>> {
        return packageList().asSequence().map { (names, pkg, _) -> names to pkg }.iterator()
    }

    open fun constantGroupsIter(): Iterator<Pair<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode>> {
        return constantGroupList().asSequence().map { (names, group, _) -> names to group }.iterator()
    }

    abstract fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>>

    abstract fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>>

    abstract fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>>

    override fun visitHeader(vararg namespaces: String) {
        mergeNs(namespaces.map { Namespace(it) }.toSet())
    }

    open fun accept(visitor: MappingVisitor, nsFilter: List<Namespace> = namespaces, sort: Boolean = false) {
        acceptInner(visitor, nsFilter, sort)
        visitor.visitEnd()
    }

    override fun acceptOuter(visitor: NullVisitor, nsFilter: Collection<Namespace>): MappingVisitor? {
        return null
    }

    override fun acceptInner(visitor: MappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        visitor.visitHeader(*nsFilter.filter { namespaces.contains(it) }.map { it.name }.toTypedArray())
        super.acceptInner(visitor, nsFilter, sort)
        val packageIter = packagesIter().asSequence().map { it.second() }
        for (pkg in if (sort) packageIter.sortedBy { it.toString() } else packageIter) {
            pkg.accept(visitor, nsFilter, sort)
        }
        val clsIter = classesIter().asSequence().map { it.second() }
        for (cls in if (sort) clsIter.sortedBy { it.toString() } else clsIter) {
            cls.accept(visitor, nsFilter, sort)
        }
        val cgIter = constantGroupsIter().asSequence().map { it.second() }
        for (group in if (sort) cgIter.sortedBy { it.toString() } else cgIter) {
            group.accept(visitor, nsFilter, sort)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        acceptInner(UMFWriter.write(::append, false), namespaces, true)
    }

}