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

open class NullDelegator : Delegator() {

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        return null
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        return null
    }

    override fun visitField(delegate: ClassVisitor, names: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
        return null
    }

    override fun visitMethod(delegate: ClassVisitor, names: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
        return null
    }

    override fun visitWildcard(
        delegate: ClassVisitor,
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        return null
    }

    override fun visitInnerClass(delegate: ClassVisitor, type: InnerClassNode.InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassVisitor? {
        return null
    }

    override fun visitParameter(delegate: InvokableVisitor<*>, index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
        return null
    }

    override fun visitLocalVariable(delegate: InvokableVisitor<*>, lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
        return null
    }

    override fun visitException(delegate: InvokableVisitor<*>, type: ExceptionType, exception: InternalName, baseNs: Namespace, namespaces: Set<Namespace>): ExceptionVisitor? {
        return null
    }

    override fun visitAccess(delegate: AccessParentVisitor<*>, type: AccessType, value: AccessFlag, conditions: AccessConditions, namespaces: Set<Namespace>): AccessVisitor? {
        return null
    }

    override fun visitJavadoc(
        delegate: JavadocParentNode<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): JavadocVisitor? {
        return null
    }

    override fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SignatureVisitor? {
        return null
    }

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        return null
    }

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return null
    }

    override fun visitConstant(
        delegate: ConstantGroupVisitor,
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantVisitor? {
        return null
    }

    override fun visitTarget(
        delegate: ConstantGroupVisitor,
        target: FullyQualifiedName,
        paramIdx: Int?
    ): TargetVisitor? {
        return null
    }

}