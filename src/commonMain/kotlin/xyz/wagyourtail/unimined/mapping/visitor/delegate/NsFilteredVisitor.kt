package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.nsFiltered(vararg ns: String) = nsFiltered(ns.map { Namespace(it) }.toSet())
fun MappingVisitor.nsFiltered(ns: Set<Namespace>) = DelegateMappingVisitor(this, NsFilteredDelegate(ns))

class NsFilteredDelegate(val ns: Set<Namespace>) : Delegator() {

    override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
        super.visitHeader(delegate, *namespaces.filter { Namespace(it) in ns }.toTypedArray())
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitPackage(delegate, n)
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitClass(delegate, n)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitField(delegate, n)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitMethod(delegate, n)
    }

    override fun visitInnerClass(
        delegate: ClassVisitor,
        type: InnerClassNode.InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitInnerClass(delegate, type, n)
    }

    override fun visitParameter(
        delegate: MethodVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitParameter(delegate, index, lvOrd, n)
    }

    override fun visitLocalVariable(
        delegate: MethodVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val n = names.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitLocalVariable(delegate, lvOrd, startOp, n)
    }

    override fun visitException(
        delegate: MethodVisitor,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        if (baseNs !in ns) return null
        return super.visitException(delegate, type, exception, baseNs, namespaces.filter { it in ns }.toSet())
    }

    override fun visitAccess(
        delegate: AccessParentVisitor<*>,
        type: AccessType,
        value: AccessFlag,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        val n = namespaces.filter { it in ns }.toSet()
        if (n.isEmpty()) return null
        return super.visitAccess(delegate, type, value, n)
    }

    override fun visitComment(delegate: CommentParentVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
        val n = values.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitComment(delegate, n)
    }

    override fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        values: Map<Namespace, String>
    ): SignatureVisitor? {
        val n = values.filterKeys { it in ns }
        if (n.isEmpty()) return null
        return super.visitSignature(delegate, n)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        if (baseNs !in ns) return null
        return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces.filter { it in ns }.toSet())
    }

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        if (baseNs !in ns) return null
        return super.visitConstantGroup(delegate, type, name, baseNs, namespaces.filter { it in ns }.toSet())
    }

}