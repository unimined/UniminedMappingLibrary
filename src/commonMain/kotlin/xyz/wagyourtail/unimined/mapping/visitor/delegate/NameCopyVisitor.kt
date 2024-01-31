package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyTo(from: Namespace, to: Set<Namespace>, context: MappingTree, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from, to, context, onlyMissing))
}

private class NameCopyDelegate(val from: Namespace, val to: Set<Namespace>, val context: MappingTree, val onlyMissing: Boolean) : Delegator() {

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitClass(delegate, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name
        }
        return super.visitClass(delegate, names)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitPackage(delegate, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name
        }
        return super.visitPackage(delegate, names)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitField(delegate, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name.first to null
        }
        return super.visitField(delegate, names)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitMethod(delegate, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name.first to null
        }
        return super.visitMethod(delegate, names)
    }

    override fun visitParameter(
        delegate: MethodVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitParameter(delegate, index, lvOrd, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name
        }
        return super.visitParameter(delegate, index, lvOrd, names)
    }

    override fun visitComment(delegate: CommentParentVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
        val values = values.toMutableMap()
        val value = values[from] ?: return super.visitComment(delegate, values)
        for (namespace in (to - values.keys)) {
            values[namespace] = value
        }
        return super.visitComment(delegate, values)
    }

    override fun visitLocalVariable(
        delegate: MethodVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val names = names.toMutableMap()
        val name = names[from] ?: return super.visitLocalVariable(delegate, lvOrd, startOp, names)
        for (namespace in (to - names.keys)) {
            names[namespace] = name
        }
        return super.visitLocalVariable(delegate, lvOrd, startOp, names)
    }

    override fun visitAccess(
        delegate: AccessParentVisitor<*>,
        type: AccessType,
        value: AccessFlag,
        namespaces: Set<Namespace>
    ): AccessVisitor? {
        if (from in namespaces) return super.visitAccess(delegate, type, value, namespaces + to)
        return super.visitAccess(delegate, type, value, namespaces)
    }

    override fun visitAnnotation(
        delegate: AnnotationParentVisitor<*>,
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation,
        namespaces: Set<Namespace>
    ): AnnotationVisitor? {
        if (from in namespaces || baseNs == from) return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces + to)
        return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces)
    }

    override fun visitClassSignature(delegate: ClassVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        val values = values.toMutableMap()
        val value = values[from] ?: return super.visitSignature(delegate, values)
        for (namespace in (to - values.keys)) {
            values[namespace] = context.mapClassSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, values)
    }

    override fun visitMethodSignature(delegate: MethodVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        val values = values.toMutableMap()
        val value = values[from] ?: return super.visitSignature(delegate, values)
        for (namespace in (to - values.keys)) {
            values[namespace] = context.mapMethodSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, values)
    }

    override fun visitFieldSignature(delegate: FieldVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        val values = values.toMutableMap()
        val value = values[from] ?: return super.visitSignature(delegate, values)
        for (namespace in (to - values.keys)) {
            values[namespace] = context.mapFieldSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, values)
    }

    override fun visitException(
        delegate: MethodVisitor,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        if (from in namespaces || baseNs == from) return super.visitException(delegate, type, exception, baseNs, namespaces + to)
        return super.visitException(delegate, type, exception, baseNs, namespaces)
    }

}