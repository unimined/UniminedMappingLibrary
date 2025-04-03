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

        fun <T> fillAllNames(toFill: Array<out Pair<Namespace, Set<Namespace>>>, names: MutableMap<Namespace, T>, onlyMissing: Boolean = true, fillWith: (T) -> T = { it }) {
            for ((from, to) in toFill) {
                val name = names[from] ?: continue
                for (namespace in names.keys.ifOnlyMissing(to, onlyMissing)) {
                    names[namespace] = fillWith(name)
                }
            }
        }

        fun <T, U> fillNames(toFill: Pair<Namespace, Set<Namespace>>, names: Map<Namespace, T>, onlyMissing: Boolean = true, fillWith: (T) -> T = { it }, write: (Map<Namespace, T>) -> U): U? {
            val (from, to) = toFill
            val names = names.toMutableMap()
            val name = names[from] ?: return null
            for (namespace in names.keys.ifOnlyMissing(to, onlyMissing)) {
                names[namespace] = fillWith(name)
            }
            return write(names)
        }

    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        var visitor: ClassVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing) {
                default.visitClass(delegate, names)
            }
        }
        return visitor ?: default.visitClass(delegate, names)
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        var visitor: PackageVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing) {
                default.visitPackage(delegate, names)
            }
        }
        return visitor ?: default.visitPackage(delegate, names)
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        var visitor: FieldVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing, { it.first to null }) {
                default.visitField(delegate, names)
            }
        }
        return visitor ?: default.visitField(delegate, names)
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        var visitor: MethodVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing, { it.first to null }) {
                default.visitMethod(delegate, names)
            }
        }
        return visitor ?: default.visitMethod(delegate, names)
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        var visitor: ParameterVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing) {
                default.visitParameter(delegate, index, lvOrd, names)
            }
        }
        return visitor ?: default.visitParameter(delegate, index, lvOrd, names)
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        var visitor: LocalVariableVisitor? = null
        for (pair in from) {
            visitor?.visitEnd()
            visitor = fillNames(pair, names, onlyMissing) {
                default.visitLocalVariable(delegate, lvOrd, startOp, names)
            }
        }
        return visitor ?: default.visitLocalVariable(delegate, lvOrd, startOp, names)
    }

}