@file:Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")

package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode

open class EmptyBaseVisitor<T: BaseVisitor<T>> : BaseVisitor<T> {

    override fun visitEnd() {}

}

open class EmptyMappingVisitor : EmptyBaseVisitor<MappingVisitor>(), MappingVisitor {
    override fun nextUnnamedNs(): Namespace {
        throw NotImplementedError()
    }

    override fun visitHeader(vararg namespaces: String) {
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        return EmptyPackageVisitor()
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        return EmptyClassVisitor()
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return EmptyConstantGroupVisitor()
    }

}

open class EmptyAccessParentVisitor<T: AccessParentVisitor<T>> : EmptyBaseVisitor<T>(), AccessParentVisitor<T> {

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        return EmptyAccessVisitor()
    }

}

open class EmptyAnnotationParentVisitor<T: AnnotationParentVisitor<T>> : EmptyBaseVisitor<T>(), AnnotationParentVisitor<T> {

    override fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor? {
        return EmptyAnnotationVisitor()
    }

}

open class EmptySignatureParentVisitor<T: SignatureParentVisitor<T>> :  EmptyBaseVisitor<T>(), SignatureParentVisitor<T> {

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        return EmptySignatureVisitor()
    }

}

open class EmptyJavadocParentVisitor<T: JavadocParentNode<T>> : EmptyBaseVisitor<T>(), JavadocParentNode<T> {

    override fun visitJavadoc(value: String, namespaces: Set<Namespace>): JavadocVisitor? {
        return EmptyJavadocVisitor()
    }

}

open class EmptyMemberVisitor<T: MemberVisitor<T>> : EmptyBaseVisitor<T>(), AccessParentVisitor<T> by EmptyAccessParentVisitor(), AnnotationParentVisitor<T> by EmptyAnnotationParentVisitor(), JavadocParentNode<T> by EmptyJavadocParentVisitor(), MemberVisitor<T> {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyPackageVisitor : EmptyBaseVisitor<PackageVisitor>(), AnnotationParentVisitor<PackageVisitor> by EmptyAnnotationParentVisitor(), JavadocParentNode<PackageVisitor> by EmptyJavadocParentVisitor(), PackageVisitor {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyClassVisitor : EmptyMemberVisitor<ClassVisitor>(), SignatureParentVisitor<ClassVisitor> by EmptySignatureParentVisitor(), AnnotationParentVisitor<ClassVisitor> by EmptyAnnotationParentVisitor(), ClassVisitor {
    override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
        return EmptyMethodVisitor()
    }

    override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
        return EmptyFieldVisitor()
    }

    override fun visitWildcard(
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        return EmptyWildcardVisitor()
    }

    override fun visitSeal(
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SealVisitor? {
        return EmptySealVisitor()
    }

    override fun visitInterface(
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): InterfaceVisitor? {
        return EmptyInterfaceVisitor()
    }

    override fun visitInnerClass(
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        return EmptyInnerClassVisitor()
    }

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyMethodVisitor : EmptyMemberVisitor<MethodVisitor>(), SignatureParentVisitor<MethodVisitor> by EmptySignatureParentVisitor(), AnnotationParentVisitor<MethodVisitor> by EmptyAnnotationParentVisitor(), MethodVisitor {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return EmptyParameterVisitor()
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return EmptyLocalVariableVisitor()
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return EmptyExceptionVisitor()
    }

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class EmptyFieldVisitor : EmptyMemberVisitor<FieldVisitor>(), SignatureParentVisitor<FieldVisitor> by EmptySignatureParentVisitor(), FieldVisitor

open class EmptyWildcardVisitor : EmptyMemberVisitor<WildcardVisitor>(), SignatureParentVisitor<WildcardVisitor> by EmptySignatureParentVisitor(), WildcardVisitor {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return EmptyParameterVisitor()
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return EmptyLocalVariableVisitor()
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return EmptyExceptionVisitor()
    }

}

open class EmptyParameterVisitor : EmptyMemberVisitor<ParameterVisitor>(), ParameterVisitor

open class EmptyLocalVariableVisitor : EmptyMemberVisitor<LocalVariableVisitor>(), LocalVariableVisitor

open class EmptyExceptionVisitor : EmptyBaseVisitor<ExceptionVisitor>(), ExceptionVisitor

open class EmptyJavadocVisitor : EmptyBaseVisitor<JavadocVisitor>(), JavadocVisitor

open class EmptySignatureVisitor : EmptyBaseVisitor<SignatureVisitor>(), SignatureVisitor

open class EmptyAccessVisitor : EmptyBaseVisitor<AccessVisitor>(), AccessVisitor

open class EmptyAnnotationVisitor : EmptyBaseVisitor<AnnotationVisitor>(), AnnotationVisitor

open class EmptyConstantGroupVisitor : EmptyBaseVisitor<ConstantGroupVisitor>(), ConstantGroupVisitor {
    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        return EmptyConstantVisitor()
    }

    override fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetVisitor? {
        return EmptyTargetVisitor()
    }

    override fun visitExpression(value: Constant, expression: Expression): ExpressionVisitor? {
        return EmptyExpressionVisitor()
    }

}

open class EmptyConstantVisitor : EmptyBaseVisitor<ConstantVisitor>(), ConstantVisitor

open class EmptyExpressionVisitor : EmptyBaseVisitor<ExpressionVisitor>(), ExpressionVisitor

open class EmptyTargetVisitor : EmptyBaseVisitor<TargetVisitor>(), TargetVisitor

open class EmptyInnerClassVisitor : EmptyBaseVisitor<InnerClassVisitor>(), AccessParentVisitor<InnerClassVisitor> by EmptyAccessParentVisitor(), InnerClassVisitor

open class EmptySealVisitor : EmptyBaseVisitor<SealVisitor>(), SealVisitor

open class EmptyInterfaceVisitor : EmptyBaseVisitor<InterfaceVisitor>(), InterfaceVisitor {}