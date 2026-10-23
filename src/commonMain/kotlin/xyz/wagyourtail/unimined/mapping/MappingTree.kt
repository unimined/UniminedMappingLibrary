package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.commonskt.utils.iterable
import xyz.wagyourtail.commonskt.utils.maybeEscape
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationElementName
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.FieldExpression
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.ThrowsSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ArrayTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ReferenceTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface MappingTree : MappingElement {

    companion object {
        operator fun invoke(namespaces: Iterable<Namespace> = emptyList()): MappingTree =
            MutableMappingTree(namespaces)
    }



    val namespaces: Iterable<Namespace>

    override val contents: Iterable<MappingElement>
        get() = iterable {
            yieldAll(packages)
            yieldAll(classes)
            yieldAll(constantGroups)
        }

    val packages: Iterable<PackageMapping>

    val classes: Iterable<ClassMapping>

    val constantGroups: Iterable<ConstantGroupMapping>

    fun getClass(namespace: Namespace, name: InternalName): ClassMapping?

    fun checkNamespace(ns: Namespace) {
        if (ns !in namespaces) {
            throw IllegalArgumentException("Invalid namespace $ns, expected one of: $namespaces")
        }
    }

    /**
     * Remaps every class reference inside a JVMS [Type] from [fromNs] to [toNs].
     *
     * [InternalName], [PackageName], and [FullyQualifiedName] use dedicated lookup
     * paths (see their overloads); all other supported types are remapped by
     * re-serializing through [remapAcceptor] and re-parsing. Supported types:
     * descriptors, name-and-descriptor pairs, signatures and signature sub-types,
     * annotations, expressions, [ObjectType], [UnqualifiedName]. Any other [Type]
     * throws [IllegalArgumentException] — add an arm when a new mappable type is
     * needed rather than silently passing it through.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Type> map(fromNs: Namespace, toNs: Namespace, value: T): T {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return value
        fun Type.reserialized() = buildString { accept(remapAcceptor(fromNs, toNs)) }
        return when (value) {
            is InternalName -> map(fromNs, toNs, value)
            is PackageName -> map(fromNs, toNs, value)
            is FullyQualifiedName -> map(fromNs, toNs, value)
            is ObjectType -> ObjectType(map(fromNs, toNs, value.getInternalName()))
            is Annotation -> Annotation.unchecked(buildString {
                val annCls = getClass(fromNs, value.getParts().first.getInternalName())
                value.accept(remapAcceptor(fromNs, toNs, annCls))
            })
            // concrete descriptor arms must precede the FieldOrMethodDescriptor
            // interface arm, or the unchecked cast back to T fails
            is FieldDescriptor -> FieldDescriptor.unchecked(value.reserialized())
            is MethodDescriptor -> MethodDescriptor.unchecked(value.reserialized())
            is FieldOrMethodDescriptor -> FieldOrMethodDescriptor.unchecked(value.reserialized())
            is FieldNameAndDescriptor -> FieldNameAndDescriptor.unchecked(value.reserialized())
            is MethodNameAndDescriptor -> MethodNameAndDescriptor.unchecked(value.reserialized())
            is Expression -> Expression.unchecked(value.reserialized())
            is ClassSignature -> ClassSignature.unchecked(value.reserialized())
            is MethodSignature -> MethodSignature.unchecked(value.reserialized())
            is FieldSignature -> FieldSignature.unchecked(value.reserialized())
            is ClassTypeSignature -> ClassTypeSignature.unchecked(value.reserialized())
            is ReferenceTypeSignature -> ReferenceTypeSignature.unchecked(value.reserialized())
            is ArrayTypeSignature -> ArrayTypeSignature.unchecked(value.reserialized())
            is JavaTypeSignature -> JavaTypeSignature.unchecked(value.reserialized())
            is ThrowsSignature -> ThrowsSignature.unchecked(value.reserialized())
            is UnqualifiedName -> UnqualifiedName.unchecked(value.reserialized())
            else -> throw IllegalArgumentException("Cannot map ${value::class.simpleName}")
        } as T
    }

    fun mapClassSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return map(fromNs, toNs, JVMS.parseClassSignature(signature)).toString()
    }

    fun mapMethodSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return map(fromNs, toNs, JVMS.parseMethodSignature(signature)).toString()
    }

    fun mapFieldSignature(fromNs: Namespace, toNs: Namespace, signature: String): String {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return signature
        return map(fromNs, toNs, JVMS.parseFieldSignature(signature)).toString()
    }

    fun map(fromNs: Namespace, toNs: Namespace, internalName: InternalName): InternalName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return internalName
        val cls = getClass(fromNs, internalName)
        if (cls != null) {
            return cls.names[toNs] ?: internalName
        }
        val parts = internalName.getParts()
        val pkg = map(fromNs, toNs, parts.first)
        return InternalName(pkg, parts.second)
    }

    fun map(fromNs: Namespace, toNs: Namespace, packageName: PackageName): PackageName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return packageName
        for (pkg in packages) {
            if (pkg.names[fromNs] == packageName) {
                return pkg.names[toNs] ?: packageName
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
        val mappedCls = cls.names[toNs] ?: parts.first.getInternalName()

        if (parts.second != null) {
            val mParts = parts.second!!.getParts()
            val mappedName = if (mParts.second == null || mParts.second!!.isFieldDescriptor()) {
                val fd = cls.getField(fromNs, mParts.first, mParts.second?.getFieldDescriptor())
                fd?.names[toNs] ?: mParts.first
            } else {
                val md = cls.getMethod(fromNs, mParts.first, mParts.second?.getMethodDescriptor())
                md?.names[toNs] ?: mParts.first
            }
            val mappedDesc = if (mParts.second == null) {
                null
            } else {
                map(fromNs, toNs, mParts.second!!)
            }
            return FullyQualifiedName(ObjectType(mappedCls), if (mappedDesc == null) mappedName.withFieldDesc(null) else NameAndDescriptor(
                mappedName,
                mappedDesc
            )
            )
        }
        return FullyQualifiedName(ObjectType(mappedCls), null)
    }

    fun map(fromNs: Namespace, toNs: Namespace, objectType: ObjectType): ObjectType {
        return ObjectType(map(fromNs, toNs, objectType.getInternalName()))
    }

    // TODO: test
    private fun StringBuilder.remapAcceptor(
        fromNs: Namespace,
        toNs: Namespace,
        annotationCtx: ClassMapping? = null,
    ): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is InternalName -> {
                    append(map(fromNs, toNs, obj))
                    false
                }
                // recursion through map() gives each nested annotation its own
                // declaring-class context for element-name lookups
                is Annotation -> {
                    append(map(fromNs, toNs, obj))
                    false
                }
                is AnnotationElementName -> {
                    val md = annotationCtx
                        ?.getMethod(fromNs, UnqualifiedName.unchecked(obj.value.unescape()), null)
                        ?.names[toNs]
                    append(md?.value?.maybeEscape() ?: obj)
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
                    append("L")
                    append(mappedOuter)
                    types?.accept(remapAcceptor(fromNs, toNs, annotationCtx))
                    for (suf in sufs) {
                        val (innerName, innerTypes) = suf.getParts().getParts()
                        clsNameBuilder.append("$").append(innerName)
                        val mappedInner = map(fromNs, toNs, InternalName.read(clsNameBuilder.toString()))
                        mappedBuilder.append("$")
                        val innerNameMapped = mappedInner.toString().substring(mappedBuilder.length)
                        mappedBuilder.append(innerNameMapped)
                        append(".")
                        append(innerNameMapped)
                        innerTypes?.accept(remapAcceptor(fromNs, toNs, annotationCtx))
                    }
                    append(";")
                    false
                }
                is FieldExpression -> {
                    val (owner, instance, nameAndDesc) = obj.getParts()
                    if (owner != null) {
                        append(map(fromNs, toNs, owner.getInternalName()))
                    }
                    if (instance) {
                        append("this.")
                    }
                    val (name, desc) = nameAndDesc
                    if (owner != null) {
                        val fd = map(fromNs, toNs, FullyQualifiedName(owner, name.withFieldDesc(desc)))
                        append(fd.name)
                    } else {
                        append(name)
                    }
                    append(";")
                    if (desc != null) {
                        append(map(fromNs, toNs, desc))
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

    fun ClassMapping.getField(namespace: Namespace, name: UnqualifiedName, desc: FieldDescriptor?): FieldMapping? {
        /*
         *  because of how resolve works, there should really be a max of 2
         *  the one that matches best will be first.
         *  ie, if desc is not-null, it'll put the match with a not-null desc first.
         *  or vis-versa for null descs.
         */
        val fields = mutableListOf<FieldMapping>()
        for (field in this.fields) {
            if (field.names[namespace] == name ||
                (field.names[namespace] == null && desc != null && field.hasDescriptor() && field.getDescriptor(namespace) == desc)) {
                if (desc == null || !field.hasDescriptor() || field.getDescriptor(namespace) == desc) {
                    // ensure best match first
                    if ((desc == null) xor field.hasDescriptor()) {
                        fields.add(0, field)
                    } else {
                        fields.add(field)
                    }
                }
            }
        }
        return fields.firstOrNull()
    }

    fun ClassMapping.getMethods(namespace: Namespace, name: UnqualifiedName, desc: MethodDescriptor?): List<MethodMapping> {
        /*
         *  because of how resolve works, there should really be a max of 2
         *  the one that matches best will be first.
         *  ie, if desc is not-null, it'll put the match with a not-null desc first.
         *  or vis-versa for null descs.
         */
        val methods = mutableListOf<MethodMapping>()
        for (method in this.methods) {
            if (method.names[namespace] == name ||
                // rows missing a name in this ns still identify via desc (e.g. `m _ named;(I)Lv; yarn`)
                (method.names[namespace] == null && desc != null && method.hasDescriptor() && method.getDescriptor(namespace) == desc)) {
                if (desc == null || !method.hasDescriptor() || method.getDescriptor(namespace) == desc) {
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

    fun ClassMapping.getMethod(namespace: Namespace, name: UnqualifiedName, desc: MethodDescriptor?): MethodMapping? {
        return getMethods(namespace, name, desc).firstOrNull()
    }

    fun ClassMapping.getWildcards(type: WildcardType, namespace: Namespace, desc: FieldOrMethodDescriptor?): List<WildcardMapping> {
        val wildcards = mutableListOf<WildcardMapping>()
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

    fun ClassMapping.getWildcard(type: WildcardType, namespace: Namespace, desc: FieldOrMethodDescriptor?): WildcardMapping? {
        return getWildcards(type, namespace, desc).firstOrNull()
    }

    fun FieldMapping.getDescriptor(namespace: Namespace): FieldDescriptor? {
        if (!hasDescriptor()) return null
        if (namespace in descs) return descs[namespace]!!
        val (from, desc) = descs.entries.first()
        return map(from, namespace, desc)
    }

    fun MethodMapping.getDescriptor(namespace: Namespace): MethodDescriptor? {
        if (!hasDescriptor()) return null
        if (namespace in descs) return descs[namespace]!!
        val (from, desc) = descs.entries.first()
        return map(from, namespace, desc)
    }

    fun WildcardMapping.getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (!hasDescriptor()) return null
        if (namespace in descs) return descs[namespace]!!
        val (from, desc) = descs.entries.first()
        return map(from, namespace, desc)
    }

}