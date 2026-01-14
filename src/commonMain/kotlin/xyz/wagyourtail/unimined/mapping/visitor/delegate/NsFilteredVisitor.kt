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

fun RootMappingVisitor.nsFiltered(vararg ns: String, inverted: Boolean = false) = nsFiltered(ns.map { Namespace(it) }.toSet(), inverted)
fun RootMappingVisitor.nsFiltered(ns: Set<Namespace>, inverted: Boolean = false) = DelegateMappingRootMappingVisitor(this, NsFilteredDelegate(ns, inverted))

class NsFilteredDelegate(val namespaces: Set<Namespace>, val inverted: Boolean) : Delegator() {

    override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
        super.visitHeader(delegate, *namespaces.filter { if (inverted) it !in this@NsFilteredDelegate.namespaces else it in this@NsFilteredDelegate.namespaces }.toTypedArray())
    }

    override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitPackage(delegate, n)
    }

    override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitClass(delegate, n)
    }

    override fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitField(delegate, n)
    }

    override fun visitMethod(delegate: ClassMappingVisitor, names: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitMethod(delegate, n)
    }

    override fun visitInnerClass(
        delegate: ClassMappingVisitor,
        type: InnerType,
        names: Map<Namespace, Pair<String, FullyQualifiedName?>>
    ): InnerClassMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitInnerClass(delegate, type, n)
    }

    override fun visitSeal(
        delegate: ClassMappingVisitor,
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace
    ): SealMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitSeal(delegate, type, name, baseNs)
    }

    override fun visitInterface(
        delegate: ClassMappingVisitor,
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace
    ): InterfaceMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitInterface(delegate, type, name, baseNs)
    }

    override fun visitWildcard(
        delegate: ClassMappingVisitor,
        type: WildcardType,
        descs: Map<Namespace, FieldOrMethodDescriptor>
    ): WildcardMappingVisitor? {
        val n = descs.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (descs.isNotEmpty() && n.isEmpty()) return null
        return super.visitWildcard(delegate, type, n)
    }

    override fun visitParameter(
        delegate: InvokableMappingVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): ParameterMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitParameter(delegate, index, lvOrd, n)
    }

    override fun visitLocalVariable(
        delegate: InvokableMappingVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        val n = names.filterKeys { if (inverted) it !in namespaces else it in namespaces }
        if (n.isEmpty()) return null
        return super.visitLocalVariable(delegate, lvOrd, startOp, n)
    }

    override fun visitException(
        delegate: InvokableMappingVisitor,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace
    ): ExceptionMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitException(delegate, type, exception, baseNs)
    }

    override fun visitJavadoc(delegate: JavadocParentMappingVisitor, value: String, baseNs: Namespace): JavadocMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitJavadoc(delegate, value, baseNs)
    }

    override fun <T: Signature>  visitSignature(delegate: SignatureParentMappingVisitor<T>, value: T, baseNs: Namespace): SignatureMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitSignature(delegate, value, baseNs)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentMappingVisitor,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation
    ): AnnotationMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitAnnotation(delegate, type, baseNs, annotation)
    }

    override fun visitConstantGroup(
        delegate: RootMappingVisitor,
        type: InlineType,
        name: String?,
        baseNs: Namespace
    ): ConstantGroupMappingVisitor? {
        if (if (inverted) baseNs in namespaces else baseNs !in namespaces) return null
        return super.visitConstantGroup(delegate, type, name, baseNs)
    }

}