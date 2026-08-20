package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*

open class NullDelegator : Delegator() {

    override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        return null
    }

    override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        return null
    }

    override fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        return null
    }

    override fun visitMethod(delegate: ClassMappingVisitor, names: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        return null
    }

    override fun visitWildcard(
        delegate: ClassMappingVisitor,
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        return null
    }

    override fun visitInnerClass(delegate: ClassMappingVisitor, type: InnerType, names: Map<Namespace, Pair<String, FullyQualifiedName?>>): InnerClassMappingVisitor? {
        return null
    }

    override fun visitSeal(
        delegate: ClassMappingVisitor,
        type: AddRemoveClear,
        name: InternalName?,
        baseNs: Namespace,
    ): SealMappingVisitor? {
        return null
    }

    override fun visitInterface(
        delegate: ClassMappingVisitor,
        type: AddRemove,
        name: ClassTypeSignature,
        baseNs: Namespace,
    ): InterfaceMappingVisitor? {
        return null
    }

    override fun visitParameter(
        delegate: InvokableMappingVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): ParameterMappingVisitor? {
        return null
    }

    override fun visitLocalVariable(
        delegate: InvokableMappingVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        return null
    }

    override fun visitException(delegate: InvokableMappingVisitor, type: AddRemove, exception: InternalName, baseNs: Namespace): ExceptionMappingVisitor? {
        return null
    }

    override fun visitAccess(delegate: AccessParentMappingVisitor, type: AddRemove, value: AccessFlag, conditions: AccessConditions): AccessMappingVisitor? {
        return null
    }

    override fun visitJavadoc(
        delegate: JavadocParentMappingVisitor,
        value: String,
        baseNs: Namespace
    ): JavadocMappingVisitor? {
        return null
    }

    override fun <T: Signature> visitSignature(
        delegate: SignatureParentMappingVisitor<T>,
        value: T,
        baseNs: Namespace
    ): SignatureMappingVisitor? {
        return null
    }

    override fun visitAnnotation(
        delegate: AnnotationParentMappingVisitor,
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation
    ): AnnotationMappingVisitor? {
        return null
    }

    override fun visitConstantGroup(
        delegate: RootMappingVisitor,
        type: InlineType,
        name: String?,
        baseNs: Namespace
    ): ConstantGroupMappingVisitor? {
        return null
    }

    override fun visitConstant(
        delegate: ConstantGroupMappingVisitor,
        fieldClass: InternalName,
        fieldName: UnqualifiedName,
        fieldDesc: FieldDescriptor?
    ): ConstantMappingVisitor? {
        return null
    }

    override fun visitTarget(
        delegate: ConstantGroupMappingVisitor,
        target: FullyQualifiedName?,
        paramIdx: Int?
    ): TargetMappingVisitor? {
        return null
    }

    override fun visitExpression(
        delegate: ConstantGroupMappingVisitor,
        value: Constant,
        expression: Expression
    ): ExpressionMappingVisitor? {
        return null
    }

}