package xyz.wagyourtail.unimined.mapping.visitor.delegate

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
import xyz.wagyourtail.unimined.mapping.visitor.*

open class Delegator(delegator: Delegator? = null) {

    val delegator: Delegator = delegator ?: this
    val default: Delegator = if (this::class == Delegator::class) this else Delegator(this)

    fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: String) {
        visitHeader(delegate, *namespaces.map { Namespace(it) }.toTypedArray())
    }

    open fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
        delegate.visitHeader(*namespaces)
    }

    open fun visitEnd(delegate: BaseMappingVisitor) {
        delegate.visitEnd()
    }

    open fun visitFooter(delegate: RootMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        return delegate.visitPackage(names)?.let { DelegatePackageMappingVisitor(it, delegator) }
    }

    open fun visitPackageEnd(delegate: PackageMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        return delegate.visitClass(names)?.let { DelegateClassMappingVisitor(it, delegator) }
    }

    open fun visitClassEnd(delegate: ClassMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        return delegate.visitField(names)?.let { DelegateFieldMappingVisitor(it, delegator) }
    }

    open fun visitFieldEnd(delegate: FieldMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethod(delegate: ClassMappingVisitor, names: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        return delegate.visitMethod(names)?.let { DelegateMethodMappingVisitor(it, delegator) }
    }

    open fun visitMethodEnd(delegate: MethodMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitWildcard(delegate: ClassMappingVisitor, type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): WildcardMappingVisitor? {
        return delegate.visitWildcard(type, descs)?.let { DelegateWildcardMappingVisitor(it, delegator) }
    }

    open fun visitWildcardEnd(delegate: WildcardMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitInnerClass(delegate: ClassMappingVisitor, type: InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassMappingVisitor? {
        return delegate.visitInnerClass(type, names)?.let { DelegateInnerClassMappingVisitor(it, delegator) }
    }

    open fun visitInnerClassEnd(delegate: InnerClassMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitInterface(delegate: ClassMappingVisitor, type: AddRemove, name: ClassTypeSignature, baseNs: Namespace): InterfaceMappingVisitor? {
        return delegate.visitInterface(type, name, baseNs)?.let { DelegateInterfaceMappingVisitor(it, delegator) }
    }

    open fun visitInterfaceEnd(delegate: InterfaceMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitEnumExtension(delegate: ClassMappingVisitor, type: AddRemove, name: UnqualifiedName, baseNs: Namespace): EnumExtensionMappingVisitor? {
        return delegate.visitEnumExtension(type, name, baseNs)?.let { DelegateEnumExtensionMappingVisitor(it, delegator) }
    }

    open fun visitEnumExtensionEnd(delegate: EnumExtensionMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitSeal(delegate: ClassMappingVisitor, type: AddRemoveClear, name: InternalName?, baseNs: Namespace): SealMappingVisitor? {
        return delegate.visitSeal(type, name, baseNs)?.let { DelegateSealMappingVisitor(it, delegator) }
    }

    open fun visitSealEnd(delegate: SealMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitParameter(delegate: InvokableMappingVisitor, index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return delegate.visitParameter(index, lvOrd, names)?.let { DelegateParameterMappingVisitor(it, delegator) }
    }

    open fun visitParameterEnd(delegate: ParameterMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodParameter(delegate: MethodMappingVisitor, index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return visitParameter(delegate, index, lvOrd, names)
    }

    open fun visitWildcardParameter(delegate: WildcardMappingVisitor, index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return visitParameter(delegate, index, lvOrd, names)
    }

    open fun visitLocalVariable(delegate: InvokableMappingVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        return delegate.visitLocalVariable(lvOrd, startOp, names)?.let { DelegateLocalVariableMappingVisitor(it, delegator) }
    }

    open fun visitLocalVariableEnd(delegate: LocalVariableMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodLocalVariable(delegate: MethodMappingVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        return visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    open fun visitWildcardLocalVariable(delegate: WildcardMappingVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        return visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    open fun visitException(delegate: InvokableMappingVisitor, type: AddRemove, exception: InternalName, baseNs: Namespace): ExceptionMappingVisitor? {
        return delegate.visitException(type, exception,baseNs)?.let { DelegateExceptionMappingVisitor(it, delegator) }
    }

    open fun visitExceptionEnd(delegate: ExceptionMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodException(delegate: MethodMappingVisitor, type: AddRemove, exception: InternalName, baseNs: Namespace): ExceptionMappingVisitor? {
        return visitException(delegate, type, exception, baseNs)
    }

    open fun visitWildcardException(delegate: WildcardMappingVisitor, type: AddRemove, exception: InternalName, baseNs: Namespace): ExceptionMappingVisitor? {
        return visitException(delegate, type, exception,baseNs)
    }

    open fun visitAccess(delegate: AccessParentMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return delegate.visitAccess(type, value, conditions)?.let { DelegateAccessMappingVisitor(it, delegator) }
    }

    open fun visitAccessEnd(delegate: AccessMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitFieldAccess(delegate: FieldMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitMethodAccess(delegate: MethodMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitWildcardAccess(delegate: WildcardMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitClassAccess(delegate: ClassMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitParameterAccess(delegate: ParameterMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitLocalVariableAccess(delegate: LocalVariableMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitInnerClassAccess(delegate: InnerClassMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return visitAccess(delegate, type, value, conditions)
    }

    open fun visitJavadoc(delegate: JavadocParentMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegate.visitJavadoc(value, baseNs)?.let { DelegateJavadocMappingVisitor(it, delegator) }
    }

    open fun visitJavadocEnd(delegate: JavadocMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitPackageJavadoc(delegate: PackageMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }


    open fun visitClassJavadoc(delegate: ClassMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun visitMethodJavadoc(delegate: MethodMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun visitWildcardJavadoc(delegate: WildcardMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun visitFieldJavadoc(delegate: FieldMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun visitParameterJavadoc(delegate: ParameterMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun visitLocalVariableJavadoc(delegate: LocalVariableMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return visitJavadoc(delegate, value, baseNs)
    }

    open fun <T: Signature> visitSignature(
        delegate: SignatureParentMappingVisitor<T>,
        value: T,
        baseNs: Namespace,
    ): SignatureMappingVisitor? {
        return delegate.visitSignature(value, baseNs)?.let { DelegateSignatureMappingVisitor(it, delegator) }
    }

    open fun visitSignatureEnd(delegate: SignatureMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassSignature(delegate: ClassMappingVisitor, value: ClassSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return visitSignature(delegate, value, baseNs)
    }

    open fun visitMethodSignature(delegate: MethodMappingVisitor, value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return visitSignature(delegate, value, baseNs)
    }

    open fun visitWildcardSignature(delegate: WildcardMappingVisitor, value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return visitSignature(delegate, value, baseNs)
    }

    open fun visitFieldSignature(delegate: FieldMappingVisitor, value: FieldSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return visitSignature(delegate, value, baseNs)
    }

    open fun visitAnnotation(delegate: AnnotationParentMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return delegate.visitAnnotation(type, baseNs, annotation)?.let { DelegateAnnotationMappingVisitor(it, delegator) }
    }

    open fun visitAnnotationEnd(delegate: AnnotationMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassAnnotation(delegate: ClassMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitMethodAnnotation(delegate: MethodMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitWildcardAnnotation(delegate: WildcardMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitFieldAnnotation(delegate: FieldMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitParameterAnnotation(delegate: ParameterMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitLocalVariableAnnotation(delegate: LocalVariableMappingVisitor, type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationMappingVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation)
    }

    open fun visitConstantGroup(
        delegate: RootMappingVisitor,
        type: InlineType,
        name: String?,
        baseNs: Namespace,
    ): ConstantGroupMappingVisitor? {
        return delegate.visitConstantGroup(type, name, baseNs)?.let { DelegateConstantGroupMappingVisitor(it, delegator) }
    }

    open fun visitConstantGroupEnd(delegate: ConstantGroupMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitConstant(
        delegate: ConstantGroupMappingVisitor,
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantMappingVisitor? {
        return delegate.visitConstant(fieldClass, fieldName, fieldDesc)?.let { DelegateConstantMappingVisitor(it, delegator) }
    }

    open fun visitConstantEnd(delegate: ConstantMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitTarget(delegate: ConstantGroupMappingVisitor, target: FullyQualifiedName?, paramIdx: Int?): TargetMappingVisitor? {
        return delegate.visitTarget(target, paramIdx)?.let { DelegateTargetMappingVisitor(it, delegator) }
    }

    open fun visitTargetEnd(delegate: TargetMappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitExpression(delegate: ConstantGroupMappingVisitor, value: Constant, expression: Expression): ExpressionMappingVisitor? {
        return delegate.visitExpression(value, expression)?.let { DelegateExpressionMappingVisitor(it, delegator) }
    }

    open fun visitExpressionEnd(delegate: ExpressionMappingVisitor) {
        visitEnd(delegate)
    }

}

abstract class DelegateBaseMappingVisitor<T: BaseMappingVisitor>(val delegate: T, val delegator: Delegator) : BaseMappingVisitor

open class DelegateMappingRootMappingVisitor(delegate: RootMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<RootMappingVisitor>(delegate, delegator), RootMappingVisitor {

    override fun visitHeader(vararg namespaces: String) {
        delegator.visitHeader(delegate, *namespaces)
    }

    override fun visitHeader(vararg namespaces: Namespace) {
        delegator.visitHeader(delegate, *namespaces)
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        return delegator.visitPackage(delegate, names)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        return delegator.visitClass(delegate, names)
    }

    override fun visitConstantGroup(
        type: InlineType,
        name: String?,
        baseNs: Namespace
    ): ConstantGroupMappingVisitor? {
        return delegator.visitConstantGroup(delegate, type, name, baseNs)
    }

    override fun visitEnd() {
        delegator.visitFooter(delegate)
    }
}

open class DelegatePackageMappingVisitor(delegate: PackageMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<PackageMappingVisitor>(delegate, delegator), PackageMappingVisitor by delegate {

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitPackageJavadoc(delegate, value, baseNs)
    }

    override fun visitEnd() {
        delegator.visitPackageEnd(delegate)
    }

}

open class DelegateClassMappingVisitor(delegate: ClassMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ClassMappingVisitor>(delegate, delegator), ClassMappingVisitor {
    override fun visitMethod(namespaces: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        return delegator.visitMethod(delegate, namespaces)
    }

    override fun visitField(namespaces: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        return delegator.visitField(delegate, namespaces)
    }

    override fun visitInnerClass(
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor? {
        return delegator.visitInnerClass(delegate, type, names)
    }

    override fun visitWildcard(
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        return delegator.visitWildcard(delegate, type, descs)
    }

    override fun visitSeal(type: AddRemoveClear, name: InternalName?, baseNs: Namespace): SealMappingVisitor? {
        return delegator.visitSeal(delegate, type, name, baseNs)
    }

    override fun visitInterface(
        type: AddRemove,
        name: ClassTypeSignature,
        baseNs: Namespace
    ): InterfaceMappingVisitor? {
        return delegator.visitInterface(delegate, type, name, baseNs)
    }

    override fun visitEnumExtension(
        type: AddRemove,
        name: UnqualifiedName,
        baseNs: Namespace
    ): EnumExtensionMappingVisitor? {
        return delegator.visitEnumExtension(delegate, type, name, baseNs)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitClassJavadoc(delegate, value, baseNs)
    }

    override fun visitSignature(value: ClassSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return delegator.visitClassSignature(delegate, value, baseNs)
    }

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions
    ): AccessMappingVisitor? {
        return delegator.visitClassAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation
    ): AnnotationMappingVisitor? {
        return delegator.visitAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitEnd() {
        delegator.visitClassEnd(delegate)
    }

}

open class DelegateMethodMappingVisitor(delegate: MethodMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<MethodMappingVisitor>(delegate, delegator), MethodMappingVisitor {

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions,
    ): AccessMappingVisitor? {
        return delegator.visitMethodAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return delegator.visitMethodAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitSignature(value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return delegator.visitMethodSignature(delegate, value, baseNs)
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return delegator.visitMethodParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        return delegator.visitMethodLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        type: AddRemove,
        exception: InternalName,
        baseNs: Namespace
    ): ExceptionMappingVisitor? {
        return delegator.visitMethodException(delegate, type, exception, baseNs)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitMethodJavadoc(delegate, value, baseNs)
    }

    override fun visitEnd() {
        delegator.visitMethodEnd(delegate)
    }

}

open class DelegateFieldMappingVisitor(delegate: FieldMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<FieldMappingVisitor>(delegate, delegator), FieldMappingVisitor {

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions,
    ): AccessMappingVisitor? {
        return delegator.visitFieldAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return delegator.visitFieldAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitSignature(value: FieldSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return delegator.visitFieldSignature(delegate, value, baseNs)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitFieldJavadoc(delegate, value, baseNs)
    }

    override fun visitEnd() {
        delegator.visitFieldEnd(delegate)
    }

}

open class DelegateWildcardMappingVisitor(delegate: WildcardMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<WildcardMappingVisitor>(delegate, delegator), WildcardMappingVisitor {

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): ParameterMappingVisitor? {
        return delegator.visitWildcardParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): LocalVariableMappingVisitor? {
        return delegator.visitWildcardLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        type: AddRemove,
        exception: InternalName,
        baseNs: Namespace,
    ): ExceptionMappingVisitor? {
        return delegator.visitWildcardException(delegate, type, exception, baseNs)
    }

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions,
    ): AccessMappingVisitor? {
        return delegator.visitWildcardAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return delegator.visitWildcardAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitWildcardJavadoc(delegate, value, baseNs)
    }

    override fun visitSignature(value: MethodSignature, baseNs: Namespace): SignatureMappingVisitor? {
        return delegator.visitWildcardSignature(delegate, value, baseNs)
    }

    override fun visitEnd() {
        delegator.visitWildcardEnd(delegate)
    }

}

open class DelegateParameterMappingVisitor(delegate: ParameterMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ParameterMappingVisitor>(delegate, delegator), ParameterMappingVisitor {

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitParameterJavadoc(delegate, value, baseNs)
    }

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions
    ): AccessMappingVisitor? {
        return delegator.visitParameterAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return delegator.visitParameterAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitEnd() {
        delegator.visitParameterEnd(delegate)
    }

}

open class DelegateLocalVariableMappingVisitor(delegate: LocalVariableMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<LocalVariableMappingVisitor>(delegate, delegator), LocalVariableMappingVisitor {

    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions,
    ): AccessMappingVisitor? {
        return delegator.visitLocalVariableAccess(delegate, type, value, condition)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return delegator.visitLocalVariableAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        return delegator.visitLocalVariableJavadoc(delegate, value, baseNs)
    }

    override fun visitEnd() {
        delegator.visitLocalVariableEnd(delegate)
    }

}

open class DelegateExceptionMappingVisitor(delegate: ExceptionMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ExceptionMappingVisitor>(delegate, delegator), ExceptionMappingVisitor {
    override fun visitEnd() {
        delegator.visitExceptionEnd(delegate)
    }
}

open class DelegateAccessMappingVisitor(delegate: AccessMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<AccessMappingVisitor>(delegate, delegator), AccessMappingVisitor {
    override fun visitEnd() {
        delegator.visitAccessEnd(delegate)
    }
}

open class DelegateJavadocMappingVisitor(delegate: JavadocMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<JavadocMappingVisitor>(delegate, delegator), JavadocMappingVisitor {
    override fun visitEnd() {
        delegator.visitJavadocEnd(delegate)
    }
}

open class DelegateSignatureMappingVisitor(delegate: SignatureMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<SignatureMappingVisitor>(delegate, delegator), SignatureMappingVisitor {
    override fun visitEnd() {
        delegator.visitSignatureEnd(delegate)
    }
}

open class DelegateAnnotationMappingVisitor(delegate: AnnotationMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<AnnotationMappingVisitor>(delegate, delegator), AnnotationMappingVisitor {
    override fun visitEnd() {
        delegator.visitAnnotationEnd(delegate)
    }
}

open class DelegateConstantGroupMappingVisitor(delegate: ConstantGroupMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ConstantGroupMappingVisitor>(delegate, delegator), ConstantGroupMappingVisitor {
    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantMappingVisitor? {
        return delegator.visitConstant(delegate, fieldClass, fieldName, fieldDesc)
    }

    override fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetMappingVisitor? {
        return delegator.visitTarget(delegate, target, paramIdx)
    }

    override fun visitExpression(value: Constant, expression: Expression): ExpressionMappingVisitor? {
        return delegator.visitExpression(delegate, value, expression)
    }

    override fun visitEnd() {
        delegator.visitConstantGroupEnd(delegate)
    }

}

open class DelegateConstantMappingVisitor(delegate: ConstantMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ConstantMappingVisitor>(delegate, delegator), ConstantMappingVisitor {
    override fun visitEnd() {
        delegator.visitConstantEnd(delegate)
    }
}

open class DelegateTargetMappingVisitor(delegate: TargetMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<TargetMappingVisitor>(delegate, delegator), TargetMappingVisitor {
    override fun visitEnd() {
        delegator.visitTargetEnd(delegate)
    }
}

open class DelegateExpressionMappingVisitor(delegate: ExpressionMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<ExpressionMappingVisitor>(delegate, delegator), ExpressionMappingVisitor {

    override fun visitEnd() {
        delegator.visitExpressionEnd(delegate)
    }

}

open class DelegateInnerClassMappingVisitor(delegate: InnerClassMappingVisitor, delegator: Delegator) : DelegateBaseMappingVisitor<InnerClassMappingVisitor>(delegate, delegator), InnerClassMappingVisitor {
    override fun visitAccess(
        type: AddRemove,
        value: AccessFlag,
        condition: AccessConditions
    ): AccessMappingVisitor? {
        return delegator.visitInnerClassAccess(delegate, type, value, condition)
    }

    override fun visitEnd() {
        delegator.visitInnerClassEnd(delegate)
    }

}

open class DelegateSealMappingVisitor(delegate: SealMappingVisitor, delegator: Delegator): DelegateBaseMappingVisitor<SealMappingVisitor>(delegate, delegator), SealMappingVisitor {

    override fun visitEnd() {
        delegator.visitSealEnd(delegate)
    }

}

open class DelegateInterfaceMappingVisitor(delegate: InterfaceMappingVisitor, delegator: Delegator): DelegateBaseMappingVisitor<InterfaceMappingVisitor>(delegate, delegator), InterfaceMappingVisitor {

    override fun visitEnd() {
        delegator.visitInterfaceEnd(delegate)
    }

}

open class DelegateEnumExtensionMappingVisitor(delegate: EnumExtensionMappingVisitor, delegator: Delegator): DelegateBaseMappingVisitor<EnumExtensionMappingVisitor>(delegate, delegator), EnumExtensionMappingVisitor {
    override fun visitEnd() {
        delegator.visitEnumExtensionEnd(delegate)
    }
}
