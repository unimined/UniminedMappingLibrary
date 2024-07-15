package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.*

open class Delegator(delegator: Delegator? = null) {

    val delegator: Delegator = delegator ?: this
    val default: Delegator = if (this::class == Delegator::class) this else Delegator(this)

    open fun nextUnnamedNs(delegate: MappingVisitor): Namespace {
        return delegate.nextUnnamedNs()
    }

    open fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
        delegate.visitHeader(*namespaces)
    }

    open fun visitEnd(delegate: BaseVisitor<*>) {
        delegate.visitEnd()
    }

    open fun visitFooter(delegate: MappingVisitor) {
        visitEnd(delegate)
    }

    open fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        return delegate.visitPackage(names)?.let { DelegatePackageVisitor(it, delegator) }
    }

    open fun visitPackageEnd(delegate: PackageVisitor) {
        visitEnd(delegate)
    }

    open fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        return delegate.visitClass(names)?.let { DelegateClassVisitor(it, delegator) }
    }

    open fun visitClassEnd(delegate: ClassVisitor) {
        visitEnd(delegate)
    }

    open fun visitField(delegate: ClassVisitor, names: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
        return delegate.visitField(names)?.let { DelegateFieldVisitor(it, delegator) }
    }

    open fun visitFieldEnd(delegate: FieldVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethod(delegate: ClassVisitor, names: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
        return delegate.visitMethod(names)?.let { DelegateMethodVisitor(it, delegator) }
    }

    open fun visitMethodEnd(delegate: MethodVisitor) {
        visitEnd(delegate)
    }

    open fun visitWildcard(delegate: ClassVisitor, type: WildcardNode.WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): WildcardVisitor? {
        return delegate.visitWildcard(type, descs)?.let { DelegateWildcardVisitor(it, delegator) }
    }

    open fun visitWildcardEnd(delegate: WildcardVisitor) {
        visitEnd(delegate)
    }

    open fun visitInnerClass(delegate: ClassVisitor, type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor? {
        return delegate.visitInnerClass(type, names)?.let { DelegateInnerClassVisitor(it, delegator) }
    }

    open fun visitInnerClassEnd(delegate: InnerClassVisitor) {
        visitEnd(delegate)
    }

    open fun visitParameter(delegate: InvokableVisitor<*>,  index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return delegate.visitParameter(index, lvOrd, names)?.let { DelegateParameterVisitor(it, delegator) }
    }

    open fun visitParameterEnd(delegate: ParameterVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodParameter(delegate: MethodVisitor, index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return visitParameter(delegate, index, lvOrd, names)
    }

    open fun visitWildcardParameter(delegate: WildcardVisitor, index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return visitParameter(delegate, index, lvOrd, names)
    }

    open fun visitLocalVariable(delegate: InvokableVisitor<*>, lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return delegate.visitLocalVariable(lvOrd, startOp, names)?.let { DelegateLocalVariableVisitor(it, delegator) }
    }

    open fun visitLocalVariableEnd(delegate: LocalVariableVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodLocalVariable(delegate: MethodVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    open fun visitWildcardLocalVariable(delegate: WildcardVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    open fun visitException(delegate: InvokableVisitor<*>, type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        return delegate.visitException(type, exception,baseNs, namespaces)?.let { DelegateExceptionVisitor(it, delegator) }
    }

    open fun visitExceptionEnd(delegate: ExceptionVisitor) {
        visitEnd(delegate)
    }

    open fun visitMethodException(delegate: MethodVisitor, type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        return visitException(delegate, type, exception, baseNs, namespaces)
    }

    open fun visitWildcardException(delegate: WildcardVisitor, type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        return visitException(delegate, type, exception,baseNs, namespaces)
    }

    open fun visitAccess(delegate: AccessParentVisitor<*>, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return delegate.visitAccess(type, value, conditions, namespaces)?.let { DelegateAccessVisitor(it, delegator) }
    }

    open fun visitAccessEnd(delegate: AccessVisitor) {
        visitEnd(delegate)
    }

    open fun visitFieldAccess(delegate: FieldVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitMethodAccess(delegate: MethodVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitWildcardAccess(delegate: WildcardVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitClassAccess(delegate: ClassVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitParameterAccess(delegate: ParameterVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitLocalVariableAccess(delegate: LocalVariableVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitInnerClassAccess(delegate: InnerClassVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitJavadoc(delegate: JavadocParentNode<*>, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegate.visitJavadoc(value, baseNs, namespaces)?.let { DelegateJavadocVisitor(it, delegator) }
    }

    open fun visitJavadocEnd(delegate: JavadocVisitor) {
        visitEnd(delegate)
    }

    open fun visitPackageJavadoc(delegate: PackageVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }


    open fun visitClassJavadoc(delegate: ClassVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitMethodJavadoc(delegate: MethodVisitor,value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitWildcardJavadoc(delegate: WildcardVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitFieldJavadoc(delegate: FieldVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitParameterJavadoc(delegate: ParameterVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitLocalVariableJavadoc(delegate: LocalVariableVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return visitJavadoc(delegate, value, baseNs, namespaces)
    }

    open fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SignatureVisitor? {
        return delegate.visitSignature(value, baseNs, namespaces)?.let { DelegateSignatureVisitor(it, delegator) }
    }

    open fun visitSignatureEnd(delegate: SignatureVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassSignature(delegate: ClassVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return visitSignature(delegate, value, baseNs, namespaces)
    }

    open fun visitMethodSignature(delegate: MethodVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return visitSignature(delegate, value, baseNs, namespaces)
    }

    open fun visitWildcardSignature(delegate: WildcardVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return visitSignature(delegate, value, baseNs, namespaces)
    }

    open fun visitFieldSignature(delegate: FieldVisitor, value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return visitSignature(delegate, value, baseNs, namespaces)
    }

    open fun visitAnnotation(delegate: AnnotationParentVisitor<*>, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return delegate.visitAnnotation(type, baseNs, annotation, namespaces)?.let { DelegateAnnotationVisitor(it, delegator) }
    }

    open fun visitAnnotationEnd(delegate: AnnotationVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassAnnotation(delegate: ClassVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitMethodAnnotation(delegate: MethodVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitWildcardAnnotation(delegate: WildcardVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitFieldAnnotation(delegate: FieldVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitParameterAnnotation(delegate: ParameterVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitLocalVariableAnnotation(delegate: LocalVariableVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return delegate.visitConstantGroup(type, name, baseNs, namespaces)?.let { DelegateConstantGroupVisitor(it, delegator) }
    }

    open fun visitConstantGroupEnd(delegate: ConstantGroupVisitor) {
        visitEnd(delegate)
    }

    open fun visitConstant(
        delegate: ConstantGroupVisitor,
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        return delegate.visitConstant(fieldClass, fieldName, fieldDesc)?.let { DelegateConstantVisitor(it, delegator) }
    }

    open fun visitConstantEnd(delegate: ConstantVisitor) {
        visitEnd(delegate)
    }

    open fun visitTarget(delegate: ConstantGroupVisitor, target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        return delegate.visitTarget(target, paramIdx)?.let { DelegateTargetVisitor(it, delegator) }
    }

    open fun visitTargetEnd(delegate: TargetVisitor) {
        visitEnd(delegate)
    }

    open fun <V> visitExtension(delegate: BaseVisitor<*>, key: String, vararg values: V): ExtensionVisitor<*, V>? {
        return delegate.visitExtension(key, *values)
    }

    open fun <V> visitExtensionEnd(delegate: ExtensionVisitor<*, V>) {
        visitEnd(delegate)
    }

}

abstract class DelegateBaseVisitor<T: BaseVisitor<T>>(val delegate: T, val delegator: Delegator) : BaseVisitor<T> {

    @Suppress("TYPE_MISMATCH")
    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        return delegator.visitExtension(delegate, key, *values)
    }

}

open class DelegateMappingVisitor(delegate: MappingVisitor, delegator: Delegator) : DelegateBaseVisitor<MappingVisitor>(delegate, delegator), MappingVisitor {
    override fun nextUnnamedNs(): Namespace {
        return delegator.nextUnnamedNs(delegate)
    }

    override fun visitHeader(vararg namespaces: String) {
        delegator.visitHeader(delegate, *namespaces)
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        return delegator.visitPackage(delegate, names)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        return delegator.visitClass(delegate, names)
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return delegator.visitConstantGroup(delegate, type, name, baseNs, namespaces)
    }

    override fun visitEnd() {
        delegator.visitFooter(delegate)
    }
}

open class DelegatePackageVisitor(delegate: PackageVisitor, delegator: Delegator) : DelegateBaseVisitor<PackageVisitor>(delegate, delegator), PackageVisitor by delegate {

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitPackageJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        return super.visitExtension(key, *values)
    }

    override fun visitEnd() {
        delegator.visitPackageEnd(delegate)
    }

}

open class DelegateClassVisitor(delegate: ClassVisitor, delegator: Delegator) : DelegateBaseVisitor<ClassVisitor>(delegate, delegator), ClassVisitor {
    override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
        return delegator.visitMethod(delegate, namespaces)
    }

    override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
        return delegator.visitField(delegate, namespaces)
    }

    override fun visitInnerClass(
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        return delegator.visitInnerClass(delegate, type, names)
    }

    override fun visitWildcard(
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        return delegator.visitWildcard(delegate, type, descs)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitClassJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return delegator.visitClassSignature(delegate, value, baseNs, namespaces)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitClassAccess(delegate, type, value, condition, namespaces)
    }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        return delegator.visitExtension(delegate, key, *values)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitEnd() {
        delegator.visitClassEnd(delegate)
    }

}

open class DelegateMethodVisitor(delegate: MethodVisitor, delegator: Delegator) : DelegateBaseVisitor<MethodVisitor>(delegate, delegator), MethodVisitor {

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        return super.visitExtension(key, *values)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitMethodAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitMethodAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return delegator.visitMethodSignature(delegate, value, baseNs, namespaces)
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return delegator.visitMethodParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return delegator.visitMethodLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return delegator.visitMethodException(delegate, type, exception, baseNs, namespaces)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitMethodJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitEnd() {
        delegator.visitMethodEnd(delegate)
    }

}

open class DelegateFieldVisitor(delegate: FieldVisitor, delegator: Delegator) : DelegateBaseVisitor<FieldVisitor>(delegate, delegator), FieldVisitor {

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitFieldAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitFieldAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return delegator.visitFieldSignature(delegate, value, baseNs, namespaces)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitFieldJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitEnd() {
        delegator.visitFieldEnd(delegate)
    }

}

open class DelegateWildcardVisitor(delegate: WildcardVisitor, delegator: Delegator) : DelegateBaseVisitor<WildcardVisitor>(delegate, delegator), WildcardVisitor {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return delegator.visitWildcardParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return delegator.visitWildcardLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return delegator.visitWildcardException(delegate, type, exception, baseNs, namespaces)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitWildcardAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitWildcardAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitWildcardJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return delegator.visitWildcardSignature(delegate, value, baseNs, namespaces)
    }

    override fun visitEnd() {
        delegator.visitWildcardEnd(delegate)
    }

}

open class DelegateParameterVisitor(delegate: ParameterVisitor, delegator: Delegator) : DelegateBaseVisitor<ParameterVisitor>(delegate, delegator), ParameterVisitor {

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitParameterJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitParameterAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitParameterAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitEnd() {
        delegator.visitParameterEnd(delegate)
    }

}

open class DelegateLocalVariableVisitor(delegate: LocalVariableVisitor, delegator: Delegator) : DelegateBaseVisitor<LocalVariableVisitor>(delegate, delegator), LocalVariableVisitor {

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitLocalVariableAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return delegator.visitLocalVariableAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        return delegator.visitLocalVariableJavadoc(delegate, value, baseNs, namespaces)
    }

    override fun visitEnd() {
        delegator.visitLocalVariableEnd(delegate)
    }

}

open class DelegateExceptionVisitor(delegate: ExceptionVisitor, delegator: Delegator) : DelegateBaseVisitor<ExceptionVisitor>(delegate, delegator), ExceptionVisitor {
    override fun visitEnd() {
        delegator.visitExceptionEnd(delegate)
    }
}

open class DelegateAccessVisitor(delegate: AccessVisitor, delegator: Delegator) : DelegateBaseVisitor<AccessVisitor>(delegate, delegator), AccessVisitor {
    override fun visitEnd() {
        delegator.visitAccessEnd(delegate)
    }
}

open class DelegateJavadocVisitor(delegate: JavadocVisitor, delegator: Delegator) : DelegateBaseVisitor<JavadocVisitor>(delegate, delegator), JavadocVisitor {
    override fun visitEnd() {
        delegator.visitJavadocEnd(delegate)
    }
}

open class DelegateSignatureVisitor(delegate: SignatureVisitor, delegator: Delegator) : DelegateBaseVisitor<SignatureVisitor>(delegate, delegator), SignatureVisitor {
    override fun visitEnd() {
        delegator.visitSignatureEnd(delegate)
    }
}

open class DelegateAnnotationVisitor(delegate: AnnotationVisitor, delegator: Delegator) : DelegateBaseVisitor<AnnotationVisitor>(delegate, delegator), AnnotationVisitor {
    override fun visitEnd() {
        delegator.visitAnnotationEnd(delegate)
    }
}

open class DelegateConstantGroupVisitor(delegate: ConstantGroupVisitor, delegator: Delegator) : DelegateBaseVisitor<ConstantGroupVisitor>(delegate, delegator), ConstantGroupVisitor {
    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        return delegator.visitConstant(delegate, fieldClass, fieldName, fieldDesc)
    }

    override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        return delegator.visitTarget(delegate, target, paramIdx)
    }

    override fun visitEnd() {
        delegator.visitConstantGroupEnd(delegate)
    }

}

open class DelegateConstantVisitor(delegate: ConstantVisitor, delegator: Delegator) : DelegateBaseVisitor<ConstantVisitor>(delegate, delegator), ConstantVisitor {
    override fun visitEnd() {
        delegator.visitConstantEnd(delegate)
    }
}

open class DelegateTargetVisitor(delegate: TargetVisitor, delegator: Delegator) : DelegateBaseVisitor<TargetVisitor>(delegate, delegator), TargetVisitor {
    override fun visitEnd() {
        delegator.visitTargetEnd(delegate)
    }
}

open class DelegateInnerClassVisitor(delegate: InnerClassVisitor, delegator: Delegator) : DelegateBaseVisitor<InnerClassVisitor>(delegate, delegator), InnerClassVisitor {
    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return delegator.visitInnerClassAccess(delegate, type, value, condition, namespaces)
    }

    override fun visitEnd() {
        delegator.visitInnerClassEnd(delegate)
    }

}
