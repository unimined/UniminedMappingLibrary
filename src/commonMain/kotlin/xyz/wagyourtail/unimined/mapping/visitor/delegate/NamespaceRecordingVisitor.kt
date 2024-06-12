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
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.recordNamespaces(recorder: (Set<Namespace>) -> Unit): MappingVisitor {
    return DelegateMappingVisitor(this, NamespaceRecordingDelegate(recorder))
}
class NamespaceRecordingDelegate(val recorder: (Set<Namespace>) -> Unit) : Delegator() {

    override fun nextUnnamedNs(delegate: MappingVisitor): Namespace {
        val ns = super.nextUnnamedNs(delegate)
        recorder(setOf(ns))
        return ns
    }

    override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
        recorder(namespaces.map { Namespace(it) }.toSet())
        super.visitHeader(delegate, *namespaces)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        recorder(names.keys)
        return super.visitPackage(delegate, names)
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        recorder(names.keys)
        return super.visitClass(delegate, names)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        recorder(names.keys)
        return super.visitField(delegate, names)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        recorder(names.keys)
        return super.visitMethod(delegate, names)
    }

    override fun visitWildcard(
        delegate: ClassVisitor,
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        recorder(descs.keys)
        return super.visitWildcard(delegate, type, descs)
    }

    override fun visitInnerClass(
        delegate: ClassVisitor,
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        recorder(names.keys)
        return super.visitInnerClass(delegate, type, names)
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        recorder(names.keys)
        return super.visitParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        recorder(names.keys)
        return super.visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitException(
        delegate: InvokableVisitor<*>,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        recorder(namespaces + baseNs)
        return super.visitException(delegate, type, exception, baseNs, namespaces)
    }

    override fun visitAccess(
        delegate: AccessParentVisitor<*>,
        type: AccessType,
        value: AccessFlag,
        conditions: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        recorder(namespaces)
        return super.visitAccess(delegate, type, value, conditions, namespaces)
    }

    override fun visitJavadoc(delegate: CommentParentVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
        recorder(values.keys)
        return super.visitJavadoc(delegate, values)
    }

    override fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        values: Map<Namespace, String>
    ): SignatureVisitor? {
        recorder(values.keys)
        return super.visitSignature(delegate, values)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        recorder(namespaces + baseNs)
        return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        recorder(namespaces + baseNs)
        return super.visitConstantGroup(delegate, type, name, baseNs, namespaces)
    }

}