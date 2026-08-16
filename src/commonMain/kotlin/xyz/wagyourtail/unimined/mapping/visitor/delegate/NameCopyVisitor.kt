package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.visitor.*
import kotlin.io.println

fun MappingVisitor.copyNames(from: Namespace, to: Set<Namespace>, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from to to, onlyMissing = onlyMissing))
}

fun MappingVisitor.copyNames(from: Pair<Namespace, Set<Namespace>>, onlyMissing: Boolean = true): MappingVisitor {
    return DelegateMappingVisitor(this, NameCopyDelegate(from, onlyMissing = onlyMissing))
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

        inline fun <U> updateNamespaces(toFill: Pair<Namespace, Set<Namespace>>, baseNs: Namespace, namespaces: Set<Namespace>, write: (Set<Namespace>) -> U): U? {
            val (from, to) = toFill
            val namespaces = namespaces.toMutableSet()

            if (baseNs == from || namespaces.contains(from)) namespaces.addAll(to)
            else return null

            return write(namespaces)
        }

    }

    override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitClass(delegate, it)
        }
    }

    override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitPackage(delegate, it)
        }
    }

    override fun visitField(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, FieldDescriptor?>>
    ): FieldVisitor? {
        return fillNames(from, names, onlyMissing, { it.first to null }) {
            default.visitField(delegate, it)
        }
    }

    override fun visitMethod(
        delegate: ClassVisitor,
        names: Map<Namespace, Pair<String, MethodDescriptor?>>
    ): MethodVisitor? {
        return fillNames(from, names, onlyMissing, { it.first to null }) {
            default.visitMethod(delegate, it)
        }
    }

    override fun visitParameter(
        delegate: InvokableVisitor<*>,
        index: Int?,
        lvOrd: Int?,
        names: Map<Namespace, String>
    ): ParameterVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitParameter(delegate, index, lvOrd, it)
        }
    }

    override fun visitLocalVariable(
        delegate: InvokableVisitor<*>,
        lvOrd: Int,
        startOp: Int?,
        names: Map<Namespace, String>
    ): LocalVariableVisitor? {
        return fillNames(from, names, onlyMissing) {
            default.visitLocalVariable(delegate, lvOrd, startOp, it)
        }
    }

    override fun visitInterface(
        delegate: ClassVisitor,
        type: InterfacesType,
        name: ClassTypeSignature,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): InterfaceVisitor? {
        return updateNamespaces(from, baseNs, namespaces) {
            default.visitInterface(delegate, type, name, baseNs, it)
        }
    }

    override fun visitException(
        delegate: InvokableVisitor<*>,
        type: ExceptionType,
        exception: InternalName,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ExceptionVisitor? {
        return updateNamespaces(from, baseNs, namespaces) {
            default.visitException(delegate, type, exception, baseNs, it)
        }
    }
}
