package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
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

    open fun visitInnerClass(delegate: ClassVisitor, type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor? {
        return delegate.visitInnerClass(type, names)?.let { DelegateInnerClassVisitor(it, delegator) }
    }

    open fun visitInnerClassEnd(delegate: InnerClassVisitor) {
        visitEnd(delegate)
    }

    open fun visitParameter(delegate: MethodVisitor,  index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return delegate.visitParameter(index, lvOrd, names)
    }

    open fun visitParameterEnd(delegate: ParameterVisitor) {
        visitEnd(delegate)
    }

    open fun visitLocalVariable(delegate: MethodVisitor, lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return delegate.visitLocalVariable(lvOrd, startOp, names)
    }

    open fun visitLocalVariableEnd(delegate: LocalVariableVisitor) {
        visitEnd(delegate)
    }

    open fun visitException(delegate: MethodVisitor, type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        return delegate.visitException(type, exception,baseNs, namespaces)
    }

    open fun visitExceptionEnd(delegate: ExceptionVisitor) {
        visitEnd(delegate)
    }

    open fun visitAccess(delegate: AccessParentVisitor<*>, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return delegate.visitAccess(type, value, conditions, namespaces)
    }

    open fun visitAccessEnd(delegate: AccessVisitor) {
        visitEnd(delegate)
    }

    open fun visitFieldAccess(delegate: FieldVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitFieldAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitMethodAccess(delegate: MethodVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitMethodAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitClassAccess(delegate: ClassVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitClassAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitParameterAccess(delegate: ParameterVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitParameterAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitLocalVariableAccess(delegate: LocalVariableVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitLocalVariableAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitInnerClassAccess(delegate: InnerClassVisitor, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return visitAccess(delegate, type, value, conditions, namespaces)
    }

    open fun visitInnerClassAccessEnd(delegate: AccessVisitor) {
        visitAccessEnd(delegate)
    }

    open fun visitJavadoc(delegate: CommentParentVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
        return delegate.visitJavadoc(values)
    }

    open fun visitJavadocEnd(delegate: CommentVisitor) {
        visitEnd(delegate)
    }

    open fun visitPackageJavadoc(delegate: PackageVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitPackageJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitClassJavadoc(delegate: ClassVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitClassJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitMethodJavadoc(delegate: MethodVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitMethodJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitFieldJavadoc(delegate: FieldVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitFieldJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitParameterJavadoc(delegate: ParameterVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitParameterJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitLocalVariableJavadoc(delegate: LocalVariableVisitor, values: Map<Namespace, String>): CommentVisitor? {
        return visitJavadoc(delegate, values)
    }

    open fun visitLocalVariableJavadocEnd(delegate: CommentVisitor) {
        visitJavadocEnd(delegate)
    }

    open fun visitSignature(delegate: SignatureParentVisitor<*>, values: Map<Namespace, String>): SignatureVisitor? {
        return delegate.visitSignature(values)
    }

    open fun visitSignatureEnd(delegate: SignatureVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassSignature(delegate: ClassVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        return visitSignature(delegate, values)
    }

    open fun visitClassSignatureEnd(delegate: SignatureVisitor) {
        visitSignatureEnd(delegate)
    }

    open fun visitMethodSignature(delegate: MethodVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        return visitSignature(delegate, values)
    }

    open fun visitMethodSignatureEnd(delegate: SignatureVisitor) {
        visitSignatureEnd(delegate)
    }

    open fun visitFieldSignature(delegate: FieldVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        return visitSignature(delegate, values)
    }

    open fun visitFieldSignatureEnd(delegate: SignatureVisitor) {
        visitSignatureEnd(delegate)
    }

    open fun visitAnnotation(delegate: AnnotationParentVisitor<*>, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return delegate.visitAnnotation(type, baseNs, annotation, namespaces)
    }

    open fun visitAnnotationEnd(delegate: AnnotationVisitor) {
        visitEnd(delegate)
    }

    open fun visitClassAnnotation(delegate: ClassVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitClassAnnotationEnd(delegate: AnnotationVisitor) {
        visitAnnotationEnd(delegate)
    }

    open fun visitMethodAnnotation(delegate: MethodVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitMethodAnnotationEnd(delegate: AnnotationVisitor) {
        visitAnnotationEnd(delegate)
    }

    open fun visitFieldAnnotation(delegate: FieldVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitFieldAnnotationEnd(delegate: AnnotationVisitor) {
        visitAnnotationEnd(delegate)
    }

    open fun visitParameterAnnotation(delegate: ParameterVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitParameterAnnotationEnd(delegate: AnnotationVisitor) {
        visitAnnotationEnd(delegate)
    }

    open fun visitLocalVariableAnnotation(delegate: LocalVariableVisitor, type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    open fun visitLocalVariableAnnotationEnd(delegate: AnnotationVisitor) {
        visitAnnotationEnd(delegate)
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
        return delegate.visitConstant(fieldClass, fieldName, fieldDesc)
    }

    open fun visitConstantEnd(delegate: ConstantVisitor) {
        visitEnd(delegate)
    }

    open fun visitTarget(delegate: ConstantGroupVisitor, target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        return delegate.visitTarget(target, paramIdx)
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

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitPackageJavadoc(delegate, values)
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

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitClassJavadoc(delegate, values)
    }

    override fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
        return delegator.visitClassSignature(delegate, values)
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

    override fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
        return delegator.visitMethodSignature(delegate, values)
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return delegator.visitParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return delegator.visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return delegator.visitException(delegate, type, exception, baseNs, namespaces)
    }

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitMethodJavadoc(delegate, values)
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

    override fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
        return delegator.visitFieldSignature(delegate, values)
    }

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitFieldJavadoc(delegate, values)
    }

    override fun visitEnd() {
        delegator.visitFieldEnd(delegate)
    }

}

open class DelegateParameterVisitor(delegate: ParameterVisitor, delegator: Delegator) : DelegateBaseVisitor<ParameterVisitor>(delegate, delegator), ParameterVisitor {

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitParameterJavadoc(delegate, values)
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

    override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
        return delegator.visitLocalVariableJavadoc(delegate, values)
    }

    override fun visitEnd() {
        delegator.visitLocalVariableEnd(delegate)
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

