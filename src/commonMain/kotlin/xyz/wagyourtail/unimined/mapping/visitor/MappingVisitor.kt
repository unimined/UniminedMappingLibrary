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
import xyz.wagyourtail.unimined.mapping.util.Scoped

interface BaseVisitor<T: BaseVisitor<T>> {

    fun visitEnd()

}

inline fun <T: BaseVisitor<T>> T.use(visitor: (@Scoped T).() -> Unit) {
    visitor(this)
    visitEnd()
}

interface NullVisitor : BaseVisitor<NullVisitor>

interface MappingVisitor : BaseVisitor<MappingVisitor> {

    fun nextUnnamedNs(): Namespace

    fun visitHeader(vararg namespaces: String)

    fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor?

    fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor?

    fun visitConstantGroup(type: ConstantGroupNode.InlineType, name: String?, baseNs: Namespace, namespaces: Set<Namespace>): ConstantGroupVisitor?

}

interface AccessParentVisitor<T: AccessParentVisitor<T>> : BaseVisitor<T> {

    fun visitAccess(type: AccessType, value: AccessFlag, condition: AccessConditions, namespaces: Set<Namespace>): AccessVisitor?

}

interface AnnotationParentVisitor<T: AnnotationParentVisitor<T>> : BaseVisitor<T> {
    fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor?
}

interface SignatureParentVisitor<T: SignatureParentVisitor<T>> : BaseVisitor<T> {

    fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor?

}

interface JavadocParentNode<T: JavadocParentNode<T>> : BaseVisitor<T> {

    fun visitJavadoc(value: String, namespaces: Set<Namespace>): JavadocVisitor?

}

interface MemberVisitor<T: MemberVisitor<T>> : AccessParentVisitor<T>, AnnotationParentVisitor<T>, JavadocParentNode<T>

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

interface PackageVisitor : AnnotationParentVisitor<PackageVisitor>, JavadocParentNode<PackageVisitor>

interface ClassVisitor : MemberVisitor<ClassVisitor>, SignatureParentVisitor<ClassVisitor> {

    fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor?

    fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor?

    fun visitInnerClass(type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor?

    fun visitWildcard(type: WildcardNode.WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): WildcardVisitor?

    fun visitSeal(type: SealedType, name: InternalName?, baseNs: Namespace, namespaces: Set<Namespace>): SealVisitor?

    fun visitInterface(type: InterfacesType, name: ClassTypeSignature, baseNs: Namespace, namespaces: Set<Namespace>): InterfaceVisitor?
}

interface InvokableVisitor<T: InvokableVisitor<T>> : MemberVisitor<T>, SignatureParentVisitor<T> {

    fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor?

    fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor?

    fun visitException(type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor?

}

interface MethodVisitor : InvokableVisitor<MethodVisitor>

interface FieldVisitor : MemberVisitor<FieldVisitor>, SignatureParentVisitor<FieldVisitor>

interface WildcardVisitor : InvokableVisitor<WildcardVisitor>

interface ParameterVisitor : MemberVisitor<ParameterVisitor>

interface LocalVariableVisitor : MemberVisitor<LocalVariableVisitor>

interface ExceptionVisitor : BaseVisitor<ExceptionVisitor>

interface JavadocVisitor : BaseVisitor<JavadocVisitor>

interface SignatureVisitor : BaseVisitor<SignatureVisitor>

interface AccessVisitor : BaseVisitor<AccessVisitor>

interface AnnotationVisitor : BaseVisitor<AnnotationVisitor>

interface ConstantGroupVisitor : BaseVisitor<ConstantGroupVisitor> {

    fun visitConstant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantVisitor?

    /**
     * @param target null means global
     */
    fun visitTarget(target: FullyQualifiedName?, paramIdx: Int?): TargetVisitor?

    fun visitExpression(value: Constant, expression: Expression): ExpressionVisitor?
}

interface ExpressionVisitor : BaseVisitor<ExpressionVisitor>

interface ConstantVisitor : BaseVisitor<ConstantVisitor>

interface TargetVisitor : BaseVisitor<TargetVisitor>

interface InnerClassVisitor : AccessParentVisitor<InnerClassVisitor>

interface SealVisitor : BaseVisitor<SealVisitor>

interface InterfaceVisitor : BaseVisitor<InterfaceVisitor>

