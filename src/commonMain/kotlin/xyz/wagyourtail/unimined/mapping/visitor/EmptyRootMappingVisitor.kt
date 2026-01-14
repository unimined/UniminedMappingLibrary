@file:Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")

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

open class EmptyBaseMappingVisitor : BaseMappingVisitor {

    override fun visitEnd() {}

}

open class EmptyRootMappingVisitor : EmptyBaseMappingVisitor(), RootMappingVisitor {

    override fun visitHeader(vararg namespaces: Namespace) {
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        return EmptyPackageMappingVisitor()
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        return EmptyClassMappingVisitor()
    }

    override fun visitConstantGroup(
        type: InlineType,
        name: String?,
        baseNs: Namespace,
    ): ConstantGroupMappingVisitor? {
        return EmptyConstantGroupMappingVisitor()
    }

}

open class EmptyAccessParentMappingVisitor : EmptyBaseMappingVisitor(), AccessParentMappingVisitor {

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
    ): AccessMappingVisitor? {
        return EmptyAccessMappingVisitor()
    }

}

open class EmptyAnnotationParentMappingVisitor : EmptyBaseMappingVisitor(), AnnotationParentMappingVisitor {

    override fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return EmptyAnnotationMappingVisitor()
    }

}

open class EmptySignatureParentMappingVisitor<T: Signature> :  EmptyBaseMappingVisitor(), SignatureParentMappingVisitor<T> {

    override fun visitSignature(value: T, baseNs: Namespace) : SignatureMappingVisitor? {
        return EmptySignatureMappingVisitor()
    }

}

open class EmptyJavadocParentMappingVisitor : EmptyBaseMappingVisitor(), JavadocParentMappingVisitor {

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return EmptyJavadocMappingVisitor()
    }

}

open class EmptyMemberMappingVisitor : EmptyBaseMappingVisitor(), AccessParentMappingVisitor by EmptyAccessParentMappingVisitor(), AnnotationParentMappingVisitor by EmptyAnnotationParentMappingVisitor(), JavadocParentMappingVisitor by EmptyJavadocParentMappingVisitor(), MemberMappingVisitor {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyPackageMappingVisitor : EmptyBaseMappingVisitor(), AnnotationParentMappingVisitor by EmptyAnnotationParentMappingVisitor(), JavadocParentMappingVisitor by EmptyJavadocParentMappingVisitor(), PackageMappingVisitor {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyClassMappingVisitor : EmptyMemberMappingVisitor(), SignatureParentMappingVisitor<ClassSignature> by EmptySignatureParentMappingVisitor(), AnnotationParentMappingVisitor by EmptyAnnotationParentMappingVisitor(), ClassMappingVisitor {
    override fun visitMethod(namespaces: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        return EmptyMethodMappingVisitor()
    }

    override fun visitField(namespaces: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        return EmptyFieldMappingVisitor()
    }

    override fun visitWildcard(
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        return EmptyWildcardMappingVisitor()
    }

    override fun visitSeal(
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace,
    ): SealMappingVisitor? {
        return EmptySealMappingVisitor()
    }

    override fun visitInterface(
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
    ): InterfaceMappingVisitor? {
        return EmptyInterfaceMappingVisitor()
    }

    override fun visitInnerClass(
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor? {
        return EmptyInnerClassMappingVisitor()
    }

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyMethodMappingVisitor : EmptyMemberMappingVisitor(), SignatureParentMappingVisitor<MethodSignature> by EmptySignatureParentMappingVisitor(), AnnotationParentMappingVisitor by EmptyAnnotationParentMappingVisitor(), MethodMappingVisitor {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return EmptyParameterMappingVisitor()
    }

    override fun visitLocalVariable(
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        return EmptyLocalVariableMappingVisitor()
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
    ): ExceptionMappingVisitor? {
        return EmptyExceptionMappingVisitor()
    }

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyFieldMappingVisitor : EmptyMemberMappingVisitor(), SignatureParentMappingVisitor<FieldSignature> by EmptySignatureParentMappingVisitor(), FieldMappingVisitor

open class EmptyWildcardMappingVisitor : EmptyMemberMappingVisitor(), SignatureParentMappingVisitor<MethodSignature> by EmptySignatureParentMappingVisitor(), WildcardMappingVisitor {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return EmptyParameterMappingVisitor()
    }

    override fun visitLocalVariable(
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        return EmptyLocalVariableMappingVisitor()
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
    ): ExceptionMappingVisitor? {
        return EmptyExceptionMappingVisitor()
    }

}

open class EmptyParameterMappingVisitor : EmptyMemberMappingVisitor(), ParameterMappingVisitor

open class EmptyLocalVariableMappingVisitor : EmptyMemberMappingVisitor(), LocalVariableMappingVisitor

open class EmptyExceptionMappingVisitor : EmptyBaseMappingVisitor(), ExceptionMappingVisitor

open class EmptyJavadocMappingVisitor : EmptyBaseMappingVisitor(), JavadocMappingVisitor

open class EmptySignatureMappingVisitor : EmptyBaseMappingVisitor(), SignatureMappingVisitor

open class EmptyAccessMappingVisitor : EmptyBaseMappingVisitor(), AccessMappingVisitor

open class EmptyAnnotationMappingVisitor : EmptyBaseMappingVisitor(), AnnotationMappingVisitor

open class EmptyConstantGroupMappingVisitor : EmptyBaseMappingVisitor(), ConstantGroupMappingVisitor {
    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantMappingVisitor? {
        return EmptyConstantMappingVisitor()
    }

    override fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetMappingVisitor? {
        return EmptyTargetMappingVisitor()
    }

    override fun visitExpression(value: Constant, expression: Expression): ExpressionMappingVisitor? {
        return EmptyExpressionMappingVisitor()
    }

}

open class EmptyConstantMappingVisitor : EmptyBaseMappingVisitor(), ConstantMappingVisitor

open class EmptyExpressionMappingVisitor : EmptyBaseMappingVisitor(), ExpressionMappingVisitor

open class EmptyTargetMappingVisitor : EmptyBaseMappingVisitor(), TargetMappingVisitor

open class EmptyInnerClassMappingVisitor : EmptyBaseMappingVisitor(), AccessParentMappingVisitor by EmptyAccessParentMappingVisitor(), InnerClassMappingVisitor

open class EmptySealMappingVisitor : EmptyBaseMappingVisitor(), SealMappingVisitor

open class EmptyInterfaceMappingVisitor : EmptyBaseMappingVisitor(), InterfaceMappingVisitor