package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyNames(from: Namespace, to: Set<Namespace>, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from, to, onlyMissing))
}

private class NameCopyDelegate(val from: Namespace, val to: Set<Namespace>, val onlyMissing: Boolean) : Delegator() {

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

    override fun visitComment(delegate: MemberVisitor<*>, values: Map<Namespace, String>): CommentVisitor? {
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

}