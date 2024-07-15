package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyTo(from: Namespace, to: Set<Namespace>, context: MemoryMappingTree, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from, to, context, onlyMissing))
}

private class NameCopyDelegate(val from: Namespace, val to: Set<Namespace>, val context: MemoryMappingTree, val onlyMissing: Boolean) : Delegator() {

    fun Set<Namespace>.ifOnlyMissing(): Set<Namespace> {
        return if (onlyMissing) to - this else to
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitClass(delegate, nameMap)
        for (namespace in nameMap.keys.ifOnlyMissing()) {
            nameMap[namespace] = name
        }
        return super.visitClass(delegate, nameMap)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitPackage(delegate, nameMap)
        for (namespace in nameMap.keys.ifOnlyMissing()) {
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
        for (namespace in nameMap.keys.ifOnlyMissing()) {
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
        for (namespace in nameMap.keys.ifOnlyMissing()) {
            nameMap[namespace] = name.first to null
        }
        return super.visitMethod(delegate, nameMap)
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitParameter(delegate, index, lvOrd, nameMap)
        for (namespace in nameMap.keys.ifOnlyMissing()) {
            nameMap[namespace] = name
        }
        return super.visitParameter(delegate, index, lvOrd, nameMap)
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val nameMap = names.toMutableMap()
        val name = nameMap[from] ?: return super.visitLocalVariable(delegate, lvOrd, startOp, nameMap)
        for (namespace in nameMap.keys.ifOnlyMissing()) {
            nameMap[namespace] = name
        }
        return super.visitLocalVariable(delegate, lvOrd, startOp, nameMap)
    }

}