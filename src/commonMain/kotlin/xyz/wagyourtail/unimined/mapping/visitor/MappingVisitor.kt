package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node.InnerClassNode

interface BaseVisitor<T: BaseVisitor<T>> {

    fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>?

}

interface NullVisitor : BaseVisitor<NullVisitor>

interface ExtensionVisitor<T: ExtensionVisitor<T, V>, V> : BaseVisitor<T>

interface MappingVisitor : BaseVisitor<MappingVisitor> {

    fun nextUnnamedNs(): Namespace

    fun visitHeader(vararg namespaces: String)

    fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor?

    fun visitConstantGroup(type: ConstantGroupNode.InlineType, baseNs: Namespace, namespaces: Set<Namespace>): ConstantGroupVisitor?

}

interface MemberVisitor<T: MemberVisitor<T>> : BaseVisitor<T> {

    fun visitComment(values: Map<Namespace, String>): CommentVisitor?

    fun visitSignature(values: Map<Namespace, String>): SignatureVisitor?

    fun visitAccess(type: AccessType, value: AccessFlag, namespaces: Set<Namespace>): AccessVisitor?

    fun visitAnnotation(type: AnnotationType, baseNs: Namespace, annotation: Annotation, namespaces: Set<Namespace>): AnnotationVisitor?

}

enum class AccessType {
    ADD,
    REMOVE
}

enum class AnnotationType {
    ADD,
    REMOVE,
    MODIFY
}

interface ClassVisitor : MemberVisitor<ClassVisitor> {

    fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor?

    fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor?

    fun visitInnerClass(type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor?

}

interface MethodVisitor : MemberVisitor<MethodVisitor> {

    fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor?

    fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor?

}

interface FieldVisitor : MemberVisitor<FieldVisitor>

interface ParameterVisitor : MemberVisitor<ParameterVisitor>

interface LocalVariableVisitor : MemberVisitor<LocalVariableVisitor>

interface CommentVisitor : BaseVisitor<CommentVisitor>

interface SignatureVisitor : BaseVisitor<SignatureVisitor>

interface AccessVisitor : BaseVisitor<AccessVisitor>

interface AnnotationVisitor : BaseVisitor<AnnotationVisitor>

interface ConstantGroupVisitor : BaseVisitor<ConstantGroupVisitor> {

    fun visitConstant(fieldClass: InternalName, fieldName: UnqualifiedName, fieldDesc: FieldDescriptor?): ConstantVisitor?

    fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor?

}

interface InnerClassVisitor : BaseVisitor<InnerClassVisitor>

interface ConstantVisitor : BaseVisitor<ConstantVisitor>

interface TargetVisitor : BaseVisitor<TargetVisitor>