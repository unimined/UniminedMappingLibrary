package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyMetadata(from: Namespace, to: Set<Namespace>, context: MemoryMappingTree): MappingVisitor {
    return DelegateMappingVisitor(this, MetadataCopyVisitor(from, to, context))
}

private class MetadataCopyVisitor(val from: Namespace, val to: Set<Namespace>, val context: MemoryMappingTree) : Delegator() {

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
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): JavadocVisitor? {
        if (baseNs == from || from in namespaces) {
            return super.visitJavadoc(delegate, value, baseNs, namespaces + to)
        }
        return super.visitJavadoc(delegate, value, baseNs, namespaces)
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
