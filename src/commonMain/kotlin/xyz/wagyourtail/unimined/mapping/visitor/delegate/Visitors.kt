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

fun RootMappingVisitor.delegator(delegator: Delegator) = DelegateMappingRootMappingVisitor(this, delegator)

fun RootMappingVisitor.mapNs(nsMap: Map<Namespace, Namespace>) = DelegateMappingRootMappingVisitor(this, object : Delegator() {

    override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
        super.visitHeader(delegate, *namespaces.map { nsMap[it] ?: it }.toTypedArray())
    }

    override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitPackage(delegate, n)
    }

    override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitClass(delegate, n)
    }

    override fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitField(delegate, n)
    }

    override fun visitMethod(delegate: ClassMappingVisitor, names: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitMethod(delegate, n)
    }

    override fun visitWildcard(
        delegate: ClassMappingVisitor,
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        val n = descs.mapKeys { nsMap[it.key] ?: it.key }
        return super.visitWildcard(delegate, type, n)
    }

    override fun visitInnerClass(
        delegate: ClassMappingVisitor,
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitInnerClass(delegate, type, n)
    }

    override fun visitSeal(
        delegate: ClassMappingVisitor,
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace,
    ): SealMappingVisitor? {
        return super.visitSeal(delegate, type, name, nsMap[baseNs] ?: baseNs)
    }

    override fun visitInterface(
        delegate: ClassMappingVisitor,
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
    ): InterfaceMappingVisitor? {
        return super.visitInterface(delegate, type, name, nsMap[baseNs] ?: baseNs)
    }

    override fun visitParameter(
        delegate: InvokableMappingVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): ParameterMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitParameter(delegate, index, lvOrd, n)
    }

    override fun visitLocalVariable(
        delegate: InvokableMappingVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitLocalVariable(delegate, lvOrd, startOp, n)
    }

    override fun visitException(
        delegate: InvokableMappingVisitor,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
    ): ExceptionMappingVisitor? {
        return super.visitException(delegate, type, exception, nsMap[baseNs] ?: baseNs)
    }

    override fun visitJavadoc(
        delegate: JavadocParentMappingVisitor,
        value: String,
        baseNs: Namespace
    ): JavadocMappingVisitor? {
        return super.visitJavadoc(delegate, value, nsMap[baseNs] ?: baseNs)
    }

    override fun <T: Signature> visitSignature(
        delegate: SignatureParentMappingVisitor<T>,
        value: T,
        baseNs: Namespace,
    ): SignatureMappingVisitor? {
        return super.visitSignature(delegate, value, nsMap[baseNs] ?: baseNs)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentMappingVisitor,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor? {
        return super.visitAnnotation(delegate, type, nsMap[baseNs] ?: baseNs, annotation)
    }

    override fun visitConstantGroup(
        delegate: RootMappingVisitor,
        type: InlineType,
        name: String?,
        baseNs: Namespace
    ): ConstantGroupMappingVisitor? {
        return super.visitConstantGroup(delegate, type, name, nsMap[baseNs] ?: baseNs)
    }

})