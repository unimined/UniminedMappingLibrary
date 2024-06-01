package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyTo(from: Namespace, to: Set<Namespace>, context: MemoryMappingTree, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from, to, context, onlyMissing))
}

private class NameCopyDelegate(val from: Namespace, val to: Set<Namespace>, val context: MemoryMappingTree, val onlyMissing: Boolean) : Delegator() {

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitClass(delegate, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name
        }
        return super.visitClass(delegate, nameMap)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitPackage(delegate, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name
        }
        return super.visitPackage(delegate, nameMap)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitField(delegate, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name.first to null
        }
        return super.visitField(delegate, nameMap)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitMethod(delegate, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name.first to null
        }
        return super.visitMethod(delegate, nameMap)
    }

    override fun visitParameter(
        delegate: MethodVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitParameter(delegate, index, lvOrd, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name
        }
        return super.visitParameter(delegate, index, lvOrd, nameMap)
    }

    override fun visitComment(delegate: CommentParentVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
        val valueMap = values.toMutableMap()
        val value = valueMap[from] ?: return super.visitComment(delegate, valueMap)
        for (namespace in (to - valueMap.keys)) {
            valueMap[namespace] = value
        }
        return super.visitComment(delegate, valueMap)
    }

    override fun visitLocalVariable(
        delegate: MethodVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitLocalVariable(delegate, lvOrd, startOp, nameMap)
        for (namespace in (to - nameMap.keys)) {
            nameMap[namespace] = name
        }
        return super.visitLocalVariable(delegate, lvOrd, startOp, nameMap)
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
        val valueMap = values.toMutableMap()
        val value = valueMap[from] ?: return super.visitSignature(delegate, valueMap)
        for (namespace in (to - valueMap.keys)) {
            valueMap[namespace] = context.mapClassSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, valueMap)
    }

    override fun visitMethodSignature(delegate: MethodVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        val valueMap = values.toMutableMap()
        val value = valueMap[from] ?: return super.visitSignature(delegate, valueMap)
        for (namespace in (to - valueMap.keys)) {
            valueMap[namespace] = context.mapMethodSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, valueMap)
    }

    override fun visitFieldSignature(delegate: FieldVisitor, values: Map<Namespace, String>): SignatureVisitor? {
        val valueMap = values.toMutableMap()
        val value = valueMap[from] ?: return super.visitSignature(delegate, valueMap)
        for (namespace in (to - valueMap.keys)) {
            valueMap[namespace] = context.mapFieldSignature(from, namespace, value)
        }
        return super.visitSignature(delegate, valueMap)
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

    override fun visitConstantGroup(
        delegate: MappingVisitor,
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return null
    }

}