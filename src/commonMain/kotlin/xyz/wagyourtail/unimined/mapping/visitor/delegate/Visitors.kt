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
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.delegator(delegator: Delegator) = DelegateMappingVisitor(this, delegator)

fun MappingVisitor.mapNs(nsMap: Map<Namespace, Namespace>) = DelegateMappingVisitor(this, object : Delegator() {

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitPackage(delegate, n)
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitClass(delegate, n)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitField(delegate, n)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitMethod(delegate, n)
    }

    override fun visitWildcard(
        delegate: ClassVisitor,
        type: WildcardNode.WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardVisitor? {
        val n = descs.mapKeys { nsMap[it.key] ?: it.key }
        return super.visitWildcard(delegate, type, n)
    }

    override fun visitInnerClass(
        delegate: ClassVisitor,
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitInnerClass(delegate, type, n)
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitParameter(delegate, index, lvOrd, n)
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val n = names.mapKeys { nsMap[it.key] ?: it.key }
        if (n.isEmpty()) return null
        return super.visitLocalVariable(delegate, lvOrd, startOp, n)
    }

    override fun visitException(
        delegate: InvokableVisitor<*>,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitException(delegate, type, exception, nsMap[baseNs] ?: baseNs, n)
    }

    override fun visitAccess(
        delegate: AccessParentVisitor<*>,
        type: AccessType,
        value: AccessFlag,
        conditions: AccessConditions,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitAccess(delegate, type, value, conditions, n)
    }

    override fun visitJavadoc(
        delegate: JavadocParentNode<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): JavadocVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitJavadoc(delegate, value, nsMap[baseNs] ?: baseNs, n)
    }

    override fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SignatureVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitSignature(delegate, value, nsMap[baseNs] ?: baseNs, n)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitAnnotation(delegate, type, nsMap[baseNs] ?: baseNs, annotation, n)
    }

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val n = namespaces.map { nsMap[it] ?: it }.toSet()
        if (n.isEmpty()) return null
        return super.visitConstantGroup(delegate, type, name, nsMap[baseNs] ?: baseNs, n)
    }

})