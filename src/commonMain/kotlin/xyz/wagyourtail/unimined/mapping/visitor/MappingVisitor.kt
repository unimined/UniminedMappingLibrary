package xyz.wagyourtail.unimined.mapping.visitor

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode

interface BaseVisitor<T: BaseVisitor<T>> {

    fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>?

}

interface NullVisitor : BaseVisitor<NullVisitor>

interface ExtensionVisitor<T: ExtensionVisitor<T, V>, V> : BaseVisitor<T> {

    fun ephemeral(): Boolean

    fun write(sink: BufferedSink)

}

interface MappingVisitor : BaseVisitor<MappingVisitor> {

    fun nextUnnamedNs(): Namespace

    fun visitHeader(vararg namespaces: String)

    fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor?

    fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor?

    fun visitConstantGroup(type: ConstantGroupNode.InlineType, name: String?, baseNs: Namespace, namespaces: Set<Namespace>): ConstantGroupVisitor?

}

interface AccessParentVisitor<T: AccessParentVisitor<T>> : BaseVisitor<T> {

    fun visitAccess(type: AccessType, value: AccessFlag, namespaces: Set<Namespace>): AccessVisitor?

}

interface AnnotationParentVisitor<T: AnnotationParentVisitor<T>> : BaseVisitor<T> {
    fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor?
}

interface SignatureParentVisitor<T: SignatureParentVisitor<T>> : BaseVisitor<T> {

    fun visitSignature(values: Map<Namespace, String>): SignatureVisitor?

}

interface CommentParentVisitor<T: CommentParentVisitor<T>> : BaseVisitor<T> {

    fun visitComment(values: Map<Namespace, String>): CommentVisitor?

}

interface MemberVisitor<T: MemberVisitor<T>> : AccessParentVisitor<T>, AnnotationParentVisitor<T>, CommentParentVisitor<T>

enum class AccessType {
    ADD,
    REMOVE
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

interface PackageVisitor : AnnotationParentVisitor<PackageVisitor>, CommentParentVisitor<PackageVisitor>

interface ClassVisitor : MemberVisitor<ClassVisitor>, SignatureParentVisitor<ClassVisitor> {

    fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor?

    fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor?

    fun visitInnerClass(type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor?

}

interface MethodVisitor : MemberVisitor<MethodVisitor>, SignatureParentVisitor<MethodVisitor> {

    fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor?

    fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor?

    fun visitException(type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor?

}

interface FieldVisitor : MemberVisitor<FieldVisitor>, SignatureParentVisitor<FieldVisitor>

interface ParameterVisitor : MemberVisitor<ParameterVisitor>

interface LocalVariableVisitor : MemberVisitor<LocalVariableVisitor>

interface ExceptionVisitor : BaseVisitor<ExceptionVisitor>

interface CommentVisitor : BaseVisitor<CommentVisitor>

interface SignatureVisitor : BaseVisitor<SignatureVisitor>

interface AccessVisitor : BaseVisitor<AccessVisitor>

interface AnnotationVisitor : BaseVisitor<AnnotationVisitor>

interface ConstantGroupVisitor : BaseVisitor<ConstantGroupVisitor> {

    fun visitConstant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantVisitor?

    fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor?

}

interface ConstantVisitor : BaseVisitor<ConstantVisitor>

interface TargetVisitor : BaseVisitor<TargetVisitor>

interface InnerClassVisitor : AccessParentVisitor<InnerClassVisitor>

