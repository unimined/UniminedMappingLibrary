package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.commonskt.utils.iterable
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.SuperInterfaceSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

interface BaseMapping {

    val contents: Iterable<BaseMapping>
        get() = iterable {}

}

interface NullMapping : BaseMapping

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

enum class AccessType {
    ADD,
    REMOVE
    ;
}

enum class AnnotationType {
    ADD,
    REMOVE,
    MODIFY
}

enum class ExceptionType {
    ADD,
    REMOVE
}

enum class SealedType {
    ADD,
    REMOVE,
    CLEAR
}

enum class InterfacesType {
    ADD,
    REMOVE
}

enum class InnerType {
    INNER,
    LOCAL,
    ANONYMOUS,
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
        }

    val methods: Iterable<MethodMapping>

    val fields: Iterable<FieldMapping>

    val innerClasses: Iterable<InnerClassMapping>

    val wildcards: Iterable<WildcardMapping>

    val seals: Iterable<SealMapping>

    val interfaces: Iterable<InterfaceMapping>
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

    val names: Map<Namespace, UnqualifiedName>
    val descs: Map<Namespace, MethodDescriptor>

}

interface FieldMapping : MemberMapping, SignatureParentMapping<FieldSignature> {

    val names: Map<Namespace, UnqualifiedName>
    val descs: Map<Namespace, FieldDescriptor>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<SignatureParentMapping>.contents)
            yieldAll(super<MemberMapping>.contents)
        }

}

interface WildcardMapping : InvokableMapping {

    val type: WildcardType
    val descs: Map<Namespace, FieldOrMethodDescriptor>

}

interface ParameterMapping : MemberMapping {

    val index: Int?
    val lvOrd: Int?
    val names: Map<Namespace, UnqualifiedName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<MemberMapping>.contents)
        }

}

interface LocalVariableMapping : MemberMapping {

    val lvOrd: Int
    val startOp: Int?
    val names: Map<Namespace, UnqualifiedName>

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<MemberMapping>.contents)
        }

}

interface ExceptionMapping : BaseMapping {

    val type: ExceptionType
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

    val type: AccessType
    val value: AccessFlag
    val condition: AccessConditions

}

interface AnnotationMapping : BaseMapping {

    val type: AnnotationType
    val baseNs: Namespace
    val annotation: Annotation

}

interface ConstantGroupMapping : BaseMapping {

    val type: InlineType
    val name: String?
    val baseNs: Namespace

    override val contents: Iterable<BaseMapping>
        get() = iterable {
            yieldAll(super<BaseMapping>.contents)
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

    val type: SealedType
    val name: InternalName?
    val baseNs: Namespace

}

interface InterfaceMapping : BaseMapping {

    val type: InterfacesType
    val name: ClassTypeSignature
    val baseNs: Namespace

}

