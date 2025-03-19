package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.visitor.*

fun MappingVisitor.copyNames(from: Namespace, to: Set<Namespace>, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from to to, onlyMissing = onlyMissing))
}

fun MappingVisitor.copyNames(vararg from: Pair<Namespace, Set<Namespace>>, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(*from, onlyMissing = onlyMissing))
}

class NameCopyDelegate(vararg val from: Pair<Namespace, Set<Namespace>>, val onlyMissing: Boolean = true) : NullDelegator() {

    companion object {

        fun Set<Namespace>.ifOnlyMissing(to: Set<Namespace>, onlyMissing: Boolean): Set<Namespace> {
            return if (onlyMissing) to - this else to
        }

        fun <T> fillNames(toFill: Array<out Pair<Namespace, Set<Namespace>>>, names: MutableMap<Namespace, T>, onlyMissing: Boolean = true, fillWith: (T) -> T = { it }) {
            for ((from, to) in toFill) {
                val name = names[from] ?: continue
                for (namespace in names.keys.ifOnlyMissing(to, onlyMissing)) {
                    names[namespace] = fillWith(name)
                }
            }
        }
    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing)
        return default.visitClass(delegate, nameMap)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing)
        return default.visitPackage(delegate, nameMap)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing) { it.first to null }
        return default.visitField(delegate, nameMap)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing) { it.first to null }
        return default.visitMethod(delegate, nameMap)
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing)
        return default.visitParameter(delegate, index, lvOrd, nameMap)
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        val nameMap = names.toMutableMap()
        fillNames(from, nameMap, onlyMissing)
        return default.visitLocalVariable(delegate, lvOrd, startOp, nameMap)
    }

}