package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.util.Scoped

interface BaseMappingVisitor {

    fun visitEnd()

}

inline fun <T: BaseMappingVisitor> T.use(visitor: (@Scoped T).() -> Unit) {
    visitor(this)
    visitEnd()
}

interface NullMappingVisitor : BaseMappingVisitor

interface RootMappingVisitor : BaseMappingVisitor {
    companion object {
        inline fun RootMappingVisitor.visitPackage(names: Map<Namespace, PackageName>, visitor: PackageMappingVisitor.() -> Unit) {
            visitPackage(names)?.use(visitor)
        }

        inline fun RootMappingVisitor.visitClass(names: Map<Namespace, InternalName>, visitor: ClassMappingVisitor.() -> Unit) {
            visitClass(names)?.use(visitor)
        }

        inline fun RootMappingVisitor.visitConstantGroup(type: InlineType, name: String?, baseNs: Namespace, visitor: ConstantGroupMappingVisitor.() -> Unit) {
            visitConstantGroup(type, name, baseNs)?.use(visitor)
        }
    }

    fun visitHeader(vararg namespaces: String) {
        visitHeader(*namespaces.map(Namespace::invoke).toTypedArray())
    }

    fun visitHeader(vararg namespaces: Namespace)

    fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor?

    fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor?

    fun visitConstantGroup(type: InlineType, name: String?, baseNs: Namespace): ConstantGroupMappingVisitor?

}

interface AccessParentMappingVisitor : BaseMappingVisitor {
    companion object {
        inline fun AccessParentMappingVisitor.visitAccess(type: AccessType, value: AccessFlag, condition: AccessConditions, visitor: AccessMappingVisitor.() -> Unit) {
            visitAccess(type, value, condition)?.use(visitor)
        }
    }

    fun visitAccess(type: AccessType, value: AccessFlag, condition: AccessConditions): AccessMappingVisitor?

}

interface AnnotationParentMappingVisitor : BaseMappingVisitor {
    companion object {
        inline fun AnnotationParentMappingVisitor.visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation, visitor: AnnotationMappingVisitor.() -> Unit) {
            visitAnnotation(type, baseNs, annotation)?.use(visitor)
        }
    }

    fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor?

}

interface SignatureParentMappingVisitor<T: Signature> : BaseMappingVisitor {
    companion object {
        inline fun <T: Signature> SignatureParentMappingVisitor<T>.visitSignature(value: T, baseNs: Namespace, visitor: SignatureMappingVisitor.() -> Unit) {
            visitSignature(value, baseNs)?.use(visitor)
        }
    }

    fun visitSignature(value: T, baseNs: Namespace): SignatureMappingVisitor?

}

interface JavadocParentMappingVisitor : BaseMappingVisitor {

    fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor?

}

interface MemberMappingVisitor : AccessParentMappingVisitor, AnnotationParentMappingVisitor, JavadocParentMappingVisitor

interface PackageMappingVisitor : AnnotationParentMappingVisitor, JavadocParentMappingVisitor

interface ClassMappingVisitor : MemberMappingVisitor, SignatureParentMappingVisitor<ClassSignature> {
    companion object {
        inline fun ClassMappingVisitor.visitMethod(namespaces: Map<Namespace, MethodNameAndDescriptor>, visitor: MethodMappingVisitor.() -> Unit) {
            visitMethod(namespaces)?.use(visitor)
        }

        inline fun ClassMappingVisitor.visitField(namespaces: Map<Namespace, FieldNameAndDescriptor>, visitor: FieldMappingVisitor.() -> Unit) {
            visitField(namespaces)?.use(visitor)
        }

        inline fun ClassMappingVisitor.visitInnerClass(type: InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>, visitor: InnerClassMappingVisitor.() -> Unit) {
            visitInnerClass(type, names)?.use(visitor)
        }

        inline fun ClassMappingVisitor.visitWildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>, visitor: WildcardMappingVisitor.() -> Unit) {
            visitWildcard(type, descs)?.use(visitor)
        }

        inline fun ClassMappingVisitor.visitSeal(type: SealedType, name: InternalName?, baseNs: Namespace, visitor: SealMappingVisitor.() -> Unit) {
            visitSeal(type, name, baseNs)?.use(visitor)
        }

        inline fun ClassMappingVisitor.visitInterface(type: InterfacesType, name: ClassTypeSignature, baseNs: Namespace, visitor: InterfaceMappingVisitor.() -> Unit) {
            visitInterface(type, name, baseNs)?.use(visitor)
        }
    }

    fun visitMethod(namespaces: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor?

    fun visitField(namespaces: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor?

    fun visitInnerClass(type: InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassMappingVisitor?

    fun visitWildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): WildcardMappingVisitor?

    fun visitSeal(type: SealedType, name: InternalName?, baseNs: Namespace): SealMappingVisitor?

    fun visitInterface(type: InterfacesType, name: ClassTypeSignature, baseNs: Namespace): InterfaceMappingVisitor?

}

interface InvokableMappingVisitor : MemberMappingVisitor, SignatureParentMappingVisitor<MethodSignature> {
    companion object {
        inline fun InvokableMappingVisitor.visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>, visitor: ParameterMappingVisitor.() -> Unit) {
            visitParameter(index, lvOrd, names)?.use(visitor)
        }

        inline fun InvokableMappingVisitor.visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>, visitor: LocalVariableMappingVisitor.() -> Unit) {
            visitLocalVariable(lvOrd, startOp, names)?.use(visitor)
        }

        inline fun InvokableMappingVisitor.visitException(type: ExceptionType, exception: InternalName, baseNs: Namespace, visitor: ExceptionMappingVisitor.() -> Unit) {
            visitException(type, exception, baseNs)?.use(visitor)
        }
    }

    fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor?

    fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor?

    fun visitException(type: ExceptionType, exception: InternalName, baseNs: Namespace): ExceptionMappingVisitor?

}

interface MethodMappingVisitor : InvokableMappingVisitor

interface FieldMappingVisitor : MemberMappingVisitor, SignatureParentMappingVisitor<FieldSignature>

interface WildcardMappingVisitor : InvokableMappingVisitor

interface ParameterMappingVisitor : MemberMappingVisitor

interface LocalVariableMappingVisitor : MemberMappingVisitor

interface ExceptionMappingVisitor : BaseMappingVisitor

interface JavadocMappingVisitor : BaseMappingVisitor

interface SignatureMappingVisitor : BaseMappingVisitor

interface AccessMappingVisitor : BaseMappingVisitor

interface AnnotationMappingVisitor : BaseMappingVisitor

interface ConstantGroupMappingVisitor : BaseMappingVisitor {
    companion object {
        fun ConstantGroupMappingVisitor.visitConstant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?, visitor: ConstantMappingVisitor.() -> Unit) {
            visitConstant(fieldClass, fieldName, fieldDesc)?.use(visitor)
        }

        fun ConstantGroupMappingVisitor.visitTarget(target: FullyQualifiedName?, paramIdx: Int?, visitor: TargetMappingVisitor.() -> Unit) {
            visitTarget(target, paramIdx)?.use(visitor)
        }

        fun ConstantGroupMappingVisitor.visitExpression(value: Constant, expression: Expression, visitor: ExpressionMappingVisitor.() -> Unit) {
            visitExpression(value, expression)?.use(visitor)
        }
    }

    fun visitConstant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantMappingVisitor?

    /**
     * @param target null means global
     */
    fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetMappingVisitor?

    fun visitExpression(value: Constant, expression: Expression): ExpressionMappingVisitor?

}

interface ExpressionMappingVisitor : BaseMappingVisitor

interface ConstantMappingVisitor : BaseMappingVisitor

interface TargetMappingVisitor : BaseMappingVisitor

interface InnerClassMappingVisitor : AccessParentMappingVisitor

interface SealMappingVisitor : BaseMappingVisitor

interface InterfaceMappingVisitor : BaseMappingVisitor
