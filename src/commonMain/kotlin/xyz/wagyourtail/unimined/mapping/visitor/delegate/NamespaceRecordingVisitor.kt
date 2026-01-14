package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*

fun RootMappingVisitor.recordNamespaces(recorder: (Set<Namespace>) -> Unit): RootMappingVisitor {
    return DelegateMappingRootMappingVisitor(this, NamespaceRecordingDelegate(recorder))
}
class NamespaceRecordingDelegate(val recorder: (Set<Namespace>) -> Unit) : Delegator() {

    fun recorder(vararg namespaces: Namespace) {
        recorder(namespaces.toSet())
    }

    override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
        recorder(*namespaces)
        super.visitHeader(delegate, *namespaces)
    }

    override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        recorder(names.keys)
        return super.visitPackage(delegate, names)
    }

    override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        recorder(names.keys)
        return super.visitClass(delegate, names)
    }

    override fun visitField(
        delegate: ClassMappingVisitor,
        names: Map<Namespace, FieldNameAndDescriptor>
    ): FieldMappingVisitor? {
        recorder(names.keys)
        return super.visitField(delegate, names)
    }

    override fun visitMethod(
        delegate: ClassMappingVisitor,
        names: Map<Namespace, MethodNameAndDescriptor>
    ): MethodMappingVisitor? {
        recorder(names.keys)
        return super.visitMethod(delegate, names)
    }

    override fun visitWildcard(
        delegate: ClassMappingVisitor,
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        recorder(descs.keys)
        return super.visitWildcard(delegate, type, descs)
    }

    override fun visitInnerClass(
        delegate: ClassMappingVisitor,
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor? {
        recorder(names.keys)
        return super.visitInnerClass(delegate, type, names)
    }

    override fun visitSeal(
        delegate: ClassMappingVisitor,
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace,
    ): SealMappingVisitor? {
        recorder(baseNs)
        return super.visitSeal(delegate, type, name, baseNs)
    }

    override fun visitInterface(
        delegate: ClassMappingVisitor,
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
    ): InterfaceMappingVisitor? {
        recorder(baseNs)
        return super.visitInterface(delegate, type, name, baseNs)
    }

    override fun visitParameter(
        delegate: InvokableMappingVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): ParameterMappingVisitor? {
        recorder(names.keys)
        return super.visitParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(
        delegate: InvokableMappingVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        recorder(names.keys)
        return super.visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        delegate: InvokableMappingVisitor,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
    ): ExceptionMappingVisitor? {
        recorder(baseNs)
        return super.visitException(delegate, type, exception, baseNs)
    }

    override fun visitJavadoc(
        delegate: JavadocParentMappingVisitor,
        value: String,
        baseNs: Namespace
    ): JavadocMappingVisitor? {
        recorder(baseNs)
        return super.visitJavadoc(delegate, value, baseNs)
    }

    override fun <T: Signature> visitSignature(
        delegate: SignatureParentMappingVisitor<T>,
        value: T,
        baseNs: Namespace
    ): SignatureMappingVisitor? {
        recorder(baseNs)
        return super.visitSignature(delegate, value, baseNs)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentMappingVisitor,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        recorder(baseNs)
        return super.visitAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitConstantGroup(
        delegate: RootMappingVisitor,
        type: InlineType,
        name: String?,
        baseNs: Namespace
    ): ConstantGroupMappingVisitor? {
        recorder(baseNs)
        return super.visitConstantGroup(delegate, type, name, baseNs)
    }

}