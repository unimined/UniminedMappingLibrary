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
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*

open class MultiBaseVisitor<T: BaseVisitor<T>>(val visitors: List<T>) : BaseVisitor<T> {

    override fun visitEnd() {
        visitors.forEach { it.visitEnd() }
    }

}

open class MultiMappingVisitor(visitors: List<MappingVisitor>): MultiBaseVisitor<MappingVisitor>(visitors), MappingVisitor {

    override fun nextUnnamedNs(): Namespace {
        return visitors.first().nextUnnamedNs()
    }

    override fun visitHeader(vararg namespaces: String) {
        visitors.forEach { it.visitHeader(*namespaces) }
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        val visitors = visitors.mapNotNull { it.visitPackage(names) }
        if (visitors.isEmpty()) return null
        return MultiPackageVisitor(visitors)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        val visitors = visitors.mapNotNull { it.visitClass(names) }
        if (visitors.isEmpty()) return null
        return MultiClassVisitor(visitors)
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val visitors = visitors.mapNotNull { it.visitConstantGroup(type, name, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiConstantGroupVisitor(visitors)
    }

}

open class MultiAccessParentVisitor<T: AccessParentVisitor<T>>(visitors: List<T>): MultiBaseVisitor<T>(visitors), AccessParentVisitor<T> {

    override fun visitAccess(
        type: AccessType,
        value: AccessFlag,
        condition: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        val visitors = visitors.mapNotNull { it.visitAccess(type, value, condition, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiAccessVisitor(visitors)
    }

}

open class MultiAnnotationParentVisitor<T: AnnotationParentVisitor<T>>(visitors: List<T>): MultiBaseVisitor<T>(visitors), AnnotationParentVisitor<T> {

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        val visitors = visitors.mapNotNull { it.visitAnnotation(type, baseNs, annotation, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiAnnotationVisitor(visitors)
    }

}

open class MultiSignatureParentVisitor<T: SignatureParentVisitor<T>>(visitors: List<T>): MultiBaseVisitor<T>(visitors), SignatureParentVisitor<T> {

    override fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
        val visitors = visitors.mapNotNull { it.visitSignature(value, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiSignatureVisitor(visitors)
    }

}

open class MultiJavadocParentVisitor<T: JavadocParentNode<T>>(visitors: List<T>): MultiBaseVisitor<T>(visitors), JavadocParentNode<T> {

    override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
        val visitors = visitors.mapNotNull { it.visitJavadoc(value, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiJavadocVisitor(visitors)
    }

}

open class MultiMemberVisitor<T: MemberVisitor<T>>(visitors: List<T>): MultiBaseVisitor<T>(visitors), MemberVisitor<T>, AccessParentVisitor<T> by MultiAccessParentVisitor(visitors), AnnotationParentVisitor<T> by MultiAnnotationParentVisitor(visitors), JavadocParentNode<T> by MultiJavadocParentVisitor(visitors) {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class MultiPackageVisitor(visitors: List<PackageVisitor>): MultiBaseVisitor<PackageVisitor>(visitors), PackageVisitor, AnnotationParentVisitor<PackageVisitor> by MultiAnnotationParentVisitor(visitors), JavadocParentNode<PackageVisitor> by MultiJavadocParentVisitor(visitors) {

    override fun visitEnd() {
        super.visitEnd()
    }

}

open class MultiClassVisitor(visitors: List<ClassVisitor>) : MultiMemberVisitor<ClassVisitor>(visitors), ClassVisitor, SignatureParentVisitor<ClassVisitor> by MultiSignatureParentVisitor(visitors) {

    override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
        val visitors = visitors.mapNotNull { it.visitMethod(namespaces) }
        if (visitors.isEmpty()) return null
        return MultiMethodVisitor(visitors)
    }

    override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
        val visitors = visitors.mapNotNull { it.visitField(namespaces) }
        if (visitors.isEmpty()) return null
        return MultiFieldVisitor(visitors)
    }

    override fun visitInnerClass(
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        val visitors = visitors.mapNotNull { it.visitInnerClass(type, names) }
        if (visitors.isEmpty()) return null
        return MultiInnerClassVisitor(visitors)
    }

    override fun visitWildcard(
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        val visitors = visitors.mapNotNull { it.visitWildcard(type, descs) }
        if (visitors.isEmpty()) return null
        return MultiWildcardVisitor(visitors)
    }

}

open class MultiMethodVisitor(visitors: List<MethodVisitor>): MultiMemberVisitor<MethodVisitor>(visitors), MethodVisitor, SignatureParentVisitor<MethodVisitor> by MultiSignatureParentVisitor(visitors) {

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        val visitors = visitors.mapNotNull { it.visitParameter(index, lvOrd, names) }
        if (visitors.isEmpty()) return null
        return MultiParameterVisitor(visitors)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        val visitors = visitors.mapNotNull { it.visitLocalVariable(lvOrd, startOp, names) }
        if (visitors.isEmpty()) return null
        return MultiLocalVariableVisitor(visitors)
    }

    override fun visitException(type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        val visitors = visitors.mapNotNull { it.visitException(type, exception, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiExceptionVisitor(visitors)
    }

}

open class MultiFieldVisitor(visitors: List<FieldVisitor>): MultiMemberVisitor<FieldVisitor>(visitors), FieldVisitor, SignatureParentVisitor<FieldVisitor> by MultiSignatureParentVisitor(visitors) {

}

open class MultiWildcardVisitor(visitors: List<WildcardVisitor>): MultiMemberVisitor<WildcardVisitor>(visitors), WildcardVisitor, SignatureParentVisitor<WildcardVisitor> by MultiSignatureParentVisitor(visitors) {
    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        val visitors = visitors.mapNotNull { it.visitParameter(index, lvOrd, names) }
        if (visitors.isEmpty()) return null
        return MultiParameterVisitor(visitors)
    }

    override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        val visitors = visitors.mapNotNull { it.visitLocalVariable(lvOrd, startOp, names) }
        if (visitors.isEmpty()) return null
        return MultiLocalVariableVisitor(visitors)
    }

    override fun visitException(
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        val visitors = visitors.mapNotNull { it.visitException(type, exception, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiExceptionVisitor(visitors)
    }

}

open class MultiParameterVisitor(visitors: List<ParameterVisitor>): MultiMemberVisitor<ParameterVisitor>(visitors), ParameterVisitor {

}

open class MultiLocalVariableVisitor(visitors: List<LocalVariableVisitor>): MultiMemberVisitor<LocalVariableVisitor>(visitors), LocalVariableVisitor {

}

open class MultiExceptionVisitor(visitors: List<ExceptionVisitor>): MultiBaseVisitor<ExceptionVisitor>(visitors), ExceptionVisitor {

}

open class MultiJavadocVisitor(visitors: List<JavadocVisitor>): MultiBaseVisitor<JavadocVisitor>(visitors), JavadocVisitor {

}

open class MultiSignatureVisitor(visitors: List<SignatureVisitor>): MultiBaseVisitor<SignatureVisitor>(visitors), SignatureVisitor {

}

open class MultiAccessVisitor(visitors: List<AccessVisitor>): MultiBaseVisitor<AccessVisitor>(visitors), AccessVisitor {

}

open class MultiAnnotationVisitor(visitors: List<AnnotationVisitor>): MultiBaseVisitor<AnnotationVisitor>(visitors), AnnotationVisitor {

}

open class MultiConstantGroupVisitor(visitors: List<ConstantGroupVisitor>): MultiBaseVisitor<ConstantGroupVisitor>(visitors), ConstantGroupVisitor {
    override fun visitConstant(
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        val visitors = visitors.mapNotNull { it.visitConstant(fieldClass, fieldName, fieldDesc) }
        if (visitors.isEmpty()) return null
        return MultiConstantVisitor(visitors)
    }

    override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
        val visitors = visitors.mapNotNull { it.visitTarget(target, paramIdx) }
        if (visitors.isEmpty()) return null
        return MultiTargetVisitor(visitors)
    }

}

open class MultiConstantVisitor(visitors: List<ConstantVisitor>): MultiBaseVisitor<ConstantVisitor>(visitors), ConstantVisitor {

}

open class MultiTargetVisitor(visitors: List<TargetVisitor>): MultiBaseVisitor<TargetVisitor>(visitors), TargetVisitor {

}

open class MultiInnerClassVisitor(visitors: List<InnerClassVisitor>): MultiAccessParentVisitor<InnerClassVisitor>(visitors), InnerClassVisitor {

}

