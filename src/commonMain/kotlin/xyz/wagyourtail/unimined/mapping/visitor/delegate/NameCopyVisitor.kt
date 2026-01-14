package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*

fun RootMappingVisitor.copyNames(from: Namespace, to: Set<Namespace>, onlyMissing: Boolean = true): RootMappingVisitor {
    return DelegateMappingRootMappingVisitor(this, NameCopyDelegate(from to to, onlyMissing = onlyMissing))
}

fun RootMappingVisitor.copyNames(from: Pair<Namespace, Set<Namespace>>, onlyMissing: Boolean = true): RootMappingVisitor {
    return DelegateMappingRootMappingVisitor(this, NameCopyDelegate(from, onlyMissing = onlyMissing))
}

class NameCopyDelegate(val from: Pair<Namespace, Set<Namespace>>, val onlyMissing: Boolean = true) : NullDelegator() {

    companion object {

        fun Set<Namespace>.ifOnlyMissing(to: Set<Namespace>, onlyMissing: Boolean): Set<Namespace> {
            return if (onlyMissing) to - this else to
        }

        inline fun <T> fillAllNames(toFill: Array<out Pair<Namespace, Set<Namespace>>>, names: MutableMap<Namespace, T>, onlyMissing: Boolean = true, fillWith: (T) -> T = { it }) {
            for ((from, to) in toFill) {
                val name = names[from] ?: continue
                for (namespace in names.keys.ifOnlyMissing(to, onlyMissing)) {
                    names[namespace] = fillWith(name)
                }
            }
        }

        inline fun <T, U> fillNames(toFill: Pair<Namespace, Set<Namespace>>, names: Map<Namespace, T>, onlyMissing: Boolean = true, fillWith: (T) -> T = { it }, write: (Map<Namespace, T>) -> U): U? {
            val (from, to) = toFill
            val names = names.toMutableMap()
            val name = names[from] ?: return null
            for (namespace in names.keys.ifOnlyMissing(to, onlyMissing)) {
                names[namespace] = fillWith(name)
            }
            return write(names)
        }

    }

    override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitClass(delegate, it)
        }
    }

    override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitPackage(delegate, it)
        }
    }

    override fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
        return fillNames(from, names, onlyMissing, { it }) {
            default.visitField(delegate, it)
        }
    }

    override fun visitMethod(delegate: ClassMappingVisitor, names: Map<Namespace, MethodNameAndDescriptor>): MethodMappingVisitor? {
        return fillNames(from, names, onlyMissing, { it }) {
            default.visitMethod(delegate, it)
        }
    }

    override fun visitParameter(
        delegate: InvokableMappingVisitor,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): ParameterMappingVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitParameter(delegate, index, lvOrd, it)
        }
    }

    override fun visitLocalVariable(
        delegate: InvokableMappingVisitor,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, UnqualifiedName>
    ): LocalVariableMappingVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitLocalVariable(delegate, lvOrd, startOp, it)
        }
    }

}