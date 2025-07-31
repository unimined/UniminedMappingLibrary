package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyMetadata(from: Namespace, to: Set<Namespace>): MappingVisitor {
    return DelegateMappingVisitor(this, MetadataCopyVisitor(from, to))
}

private class MetadataCopyVisitor(val from: Namespace, val to: Set<Namespace>) : Delegator() {

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces + to)
        }
        return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitJavadoc(
        delegate: JavadocParentNode<*>,
        value: String,
        namespaces: Set<Namespace>
    ): JavadocVisitor? {
        if (from in namespaces) {
            return super.visitJavadoc(delegate, value, namespaces + to)
        }
        return super.visitJavadoc(delegate, value, namespaces)
    }

    override fun visitException(
        delegate: InvokableVisitor<*>,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitException(delegate, type, exception, baseNs, namespaces + to)
        }
        return super.visitException(delegate, type, exception, baseNs, namespaces)
    }

    override fun visitSignature(
        delegate: SignatureParentVisitor<*>,
        value: String,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SignatureVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitSignature(delegate, value, baseNs, namespaces + to)
        }
        return super.visitSignature(delegate, value, baseNs, namespaces)
    }

    override fun visitInterface(
        delegate: ClassVisitor,
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): InterfaceVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitInterface(delegate, type, name, baseNs, namespaces + to)
        }
        return super.visitInterface(delegate, type, name, baseNs, namespaces)
    }

    override fun visitSeal(
        delegate: ClassVisitor,
        type: SealedType,
        name: InternalName?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): SealVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitSeal(delegate, type, name, baseNs, namespaces + to)
        }
        return super.visitSeal(delegate, type, name, baseNs, namespaces)
    }

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitConstantGroup(delegate, type, name, baseNs, namespaces + to)
        }
        return super.visitConstantGroup(delegate, type, name, baseNs, namespaces)
    }


}
