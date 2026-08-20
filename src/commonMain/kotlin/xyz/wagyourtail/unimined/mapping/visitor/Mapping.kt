package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.commonskt.utils.iterable
import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.commonskt.utils.maybeEscape
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationElementName
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.FieldExpression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface BaseMapping {

    val contents: Iterable<BaseMapping>
        get() = iterable {}

}

interface MappingTree : BaseMapping {

    val namespaces: Iterable<Namespace>

    override val contents: Iterable<BaseMapping>
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
            annotation.accept(annotationRemapAcceptor(fromNs , toNs, annotation))
        })
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

    fun map(fromNs: Namespace, toNs: Namespace, expression: Expression): Expression {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return expression
        return Expression.unchecked(buildString {
            expression.accept(expressionRemapAcceptor(fromNs, toNs))
        })
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
                    val mapped = getClass(fromNs, obj)?.names[toNs]
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
                        val md = cls.getMethod(fromNs, UnqualifiedName.unchecked(obj.value.unescape()), null)?.names[toNs]
                        if (md != null) {
                            append(md.value.maybeEscape())
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


    private fun StringBuilder.expressionRemapAcceptor(fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
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
            if (field.names[namespace] == name) {
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
            if (method.names[namespace] == name) {
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

interface AccessParentMapping : BaseMapping {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(access)
        }

    val access: Iterable<AccessMapping>

}

interface AnnotationParentMapping : BaseMapping {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(annotations)
        }

    val annotations: Iterable<AnnotationMapping>

}

interface SignatureParentMapping<T: Signature> : BaseMapping {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(signatures)
        }

    val signatures: Iterable<SignatureMapping<T>>

}

interface JavadocParentMapping : BaseMapping {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super.contents)
            yieldAll(javadoc)
        }

    val javadoc: Iterable<JavadocMapping>

}

interface MemberMapping : AccessParentMapping, AnnotationParentMapping, JavadocParentMapping {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<JavadocParentMapping>.contents)
            yieldAll(super<AnnotationParentMapping>.contents)
            yieldAll(super<AccessParentMapping>.contents)
        }

}

enum class AddRemove(val key: Char) {
    ADD('+'),
    REMOVE('-');

    companion object {
        val byKey = entries.associateBy { it.key }

        init {
            // assert all keys are unique
            if (byKey.size != entries.size) {
                throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
            }
        }
    }
}

enum class AddRemoveModify(val key: Char) {
    ADD('+'),
    REMOVE('-'),
    MODIFY('m');

    companion object {
        val byKey = entries.associateBy { it.key }

        init {
            // assert all keys are unique
            if (byKey.size != entries.size) {
                throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
            }
        }
    }
}


enum class AddRemoveClear(val key: Char) {
    ADD('+'),
    REMOVE('-'),
    CLEAR('c');

    companion object {
        val byKey = entries.associateBy { it.key }

        init {
            // assert all keys are unique
            if (byKey.size != entries.size) {
                throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
            }
        }
    }
}

enum class InnerType(val key: Char) {
    INNER('i'),
    LOCAL('l'),
    ANONYMOUS('a');

    companion object {
        val byKey = entries.associateBy { it.key }

        init {
            // assert all keys are unique
            if (byKey.size != entries.size) {
                throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
            }
        }
    }
}

enum class InlineType {
    PLAIN,
    BITFIELD
}

enum class WildcardType {
    METHOD,
    FIELD
    ;

    fun asElementType(): ElementType {
        return when (this) {
            METHOD -> ElementType.METHOD
            FIELD -> ElementType.FIELD
        }
    }
}

interface PackageMapping : AnnotationParentMapping, JavadocParentMapping {

    val names: Map<Namespace, PackageName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<JavadocParentMapping>.contents)
            yieldAll(super<AnnotationParentMapping>.contents)
        }

}

interface ClassMapping : MemberMapping, SignatureParentMapping<ClassSignature> {

    val names: Map<Namespace, InternalName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<SignatureParentMapping>.contents)
            yieldAll(super<MemberMapping>.contents)
            yieldAll(methods)
            yieldAll(fields)
            yieldAll(innerClasses)
            yieldAll(wildcards)
            yieldAll(seals)
            yieldAll(interfaces)
            yieldAll(enumExtensions)
        }

    val methods: Iterable<MethodMapping>

    val fields: Iterable<FieldMapping>

    val innerClasses: Iterable<InnerClassMapping>

    val wildcards: Iterable<WildcardMapping>

    val seals: Iterable<SealMapping>

    val interfaces: Iterable<InterfaceMapping>

    val enumExtensions: Iterable<EnumExtensionMapping>

}

interface InvokableMapping : MemberMapping, SignatureParentMapping<MethodSignature> {

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<SignatureParentMapping>.contents)
            yieldAll(super<MemberMapping>.contents)
            yieldAll(parameters)
            yieldAll(localVariables)
            yieldAll(exceptions)
        }

    val parameters: Iterable<ParameterMapping>

    val localVariables: Iterable<LocalVariableMapping>

    val exceptions: Iterable<ExceptionMapping>

}

interface MethodMapping : InvokableMapping {

    val entries: Map<Namespace, MethodNameAndDescriptor>

    val names: Map<Namespace, UnqualifiedName> get() = entries.mapValues { it.value.name }
    val descs: Map<Namespace, MethodDescriptor> get() = entries.mapNotNullValues { it.value.descriptor }

    fun hasDescriptor() = entries.values.any { it.descriptor != null }

}

interface FieldMapping : MemberMapping, SignatureParentMapping<FieldSignature> {

    val entries: Map<Namespace, FieldNameAndDescriptor>

    val names: Map<Namespace, UnqualifiedName> get() = entries.mapValues { it.value.name }
    val descs: Map<Namespace, FieldDescriptor> get() = entries.mapNotNullValues { it.value.descriptor }

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<SignatureParentMapping>.contents)
            yieldAll(super<MemberMapping>.contents)
        }

    fun hasDescriptor() = entries.values.any { it.descriptor != null }

}

interface WildcardMapping : InvokableMapping {

    val type: WildcardType
    val descs: Map<Namespace, FieldOrMethodDescriptor>

    fun hasDescriptor() = descs.isNotEmpty()

}

interface ParameterMapping : MemberMapping {

    val index: Int?
    val lvOrd: Int?
    val names: Map<Namespace, UnqualifiedName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super.contents)
        }

}

interface LocalVariableMapping : MemberMapping {

    val lvOrd: Int
    val startOp: Int?
    val names: Map<Namespace, UnqualifiedName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super.contents)
        }

}

interface ExceptionMapping : BaseMapping {

    val type: AddRemove
    val exception: InternalName
    val baseNs: Namespace

}

interface JavadocMapping : BaseMapping {

    val value: String
    val baseNs: Namespace

}

interface SignatureMapping<T: Signature> : BaseMapping {

    val value: T
    val baseNs: Namespace

}

interface AccessMapping : BaseMapping {

    val type: AddRemove
    val value: AccessFlag
    val condition: AccessConditions

    fun apply(set: MutableSet<AccessFlag>) {
        if (condition.check(set)) {
            when (type) {
                AddRemove.ADD -> set.add(value)
                AddRemove.REMOVE -> set.remove(value)
            }
        }
    }

}

interface AnnotationMapping : BaseMapping {

    val type: AddRemoveModify
    val baseNs: Namespace
    val annotation: Annotation

}

interface ConstantGroupMapping : BaseMapping {

    val type: InlineType
    val name: String?
    val baseNs: Namespace

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super.contents)
            yieldAll(constants)
            yieldAll(targets)
            yieldAll(expressions)
        }

    val constants: Iterable<ConstantMapping>

    val targets: Iterable<TargetMapping>

    val expressions: Iterable<ExpressionMapping>

}

interface ExpressionMapping : BaseMapping {

    val value: Constant
    val expression: Expression

}

interface ConstantMapping : BaseMapping {

    val owner: InternalName
    val field: FieldNameAndDescriptor

}

interface TargetMapping : BaseMapping {

    val target: FullyQualifiedName?
    val paramIdx: Int?

}

interface InnerClassMapping : AccessParentMapping {

    val type: InnerType
    val names: Map<Namespace, Pair<String, FullyQualifiedName?>>

}

interface SealMapping : BaseMapping {

    val type: AddRemoveClear
    val name: InternalName?
    val baseNs: Namespace

}

interface InterfaceMapping : BaseMapping {

    val type: AddRemove
    val name: ClassTypeSignature
    val baseNs: Namespace

}

interface EnumExtensionMapping : BaseMapping {

    val type: AddRemove
    val name: UnqualifiedName
    val baseNs: Namespace

}