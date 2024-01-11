package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView

/**
 * This visitor is designed to be implemented as a visitor with a stack of elements...
 * so basically, when you visit a element, it gets pushed to the stack so you can visit it's children when it returns true.
 * visitEnd then pops the element from the stack.
 */
interface ElementVisitor {

    fun visitHeader(): Boolean

    fun visitNamespaces(vararg namespaces: String): Boolean
    fun visitMetadata(vararg metadata: Pair<String, String?>): Boolean

    fun visitContent(): Boolean

    @Deprecated("use one of the visit methods directly")
    fun visit(elementType: ElementType, vararg content: String?): Boolean {
        return when (elementType) {
            ElementType.CLASS -> {
                visitClass(*content.toList().zipWithNext().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.METHOD -> {
                val seq = content.iterator()
                val desc = seq.next()
                visitMethod(desc!!, *seq.asSequence().zipWithNext().toList().toTypedArray() as Array<Pair<String, String?>>)

            }
            ElementType.FIELD -> {
                val seq = content.iterator()
                val desc = seq.next()
                visitField(desc!!, *seq.asSequence().zipWithNext().toList().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.PARAMETER -> {
                val seq = content.iterator()
                val index = seq.next()?.toIntOrNull()
                val lvOrd = seq.next()?.toIntOrNull()
                visitParameter(index, lvOrd, *seq.asSequence().zipWithNext().toList().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.VARIABLE -> {
                val seq = content.iterator()
                val lvOrd = seq.next()?.toIntOrNull()
                val startOp = seq.next()?.toIntOrNull()
                visitVariable(lvOrd, startOp, *seq.asSequence().zipWithNext().toList().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.INNER_CLASS -> {
                val seq = content.iterator()
                val type = InnerClassPropertyView.InnerType.valueOf(seq.next()!!)
                val desc = seq.next()
                visitInnerClass(type, desc!!, *seq.asSequence().zipWithNext().toList().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.SIGNATURE -> {
                visitSignature(*content.toList().zipWithNext().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.COMMENT -> {
                visitComment(*content.toList().zipWithNext().toTypedArray() as Array<Pair<String, String?>>)
            }
            ElementType.ANNOTATION -> {
                val seq = content.iterator()
                val action = AnnotationPropertyView.Action.valueOf(seq.next()!!)
                val key = seq.next()
                val value = seq.next()
                visitAnnotation(action, key!!, value!!, *seq.asSequence().toList().toTypedArray() as Array<String>)
            }
            ElementType.ACCESS -> {
                val seq = content.iterator()
                val action = AccessPropertyView.Action.valueOf(seq.next()!!)
                val access = seq.next()
                visitAccess(action, access!!, *seq.asSequence().toList().toTypedArray() as Array<String>)
            }
            ElementType.EXTENSION -> {
                val seq = content.iterator()
                val key = seq.next()
                visitExtension(key!!, *seq.asSequence().toList().toTypedArray())
            }
        }
    }

    fun visitClass(vararg names: Pair<String, String?>): Boolean

    fun visitMethod(desc: String, vararg names: Pair<String, String?>): Boolean

    fun visitField(desc: String, vararg names: Pair<String, String?>): Boolean

    fun visitParameter(index: Int?, lvOrd: Int?, vararg names: Pair<String, String?>): Boolean

    fun visitVariable(lvOrd: Int?, startOp: Int?, vararg names: Pair<String, String?>): Boolean

    fun visitInnerClass(type: InnerClassPropertyView.InnerType, desc: String, vararg names: Pair<String, String?>): Boolean

    fun visitSignature(vararg names: Pair<String, String?>): Boolean

    fun visitComment(vararg names: Pair<String, String?>): Boolean

    fun visitAnnotation(action: AnnotationPropertyView.Action, key: String, value: String, vararg namespaces: String): Boolean

    fun visitAccess(action: AccessPropertyView.Action, access: String, vararg namespaces: String): Boolean

    fun visitExtension(key: String, vararg values: String?): Boolean

    fun visitEnd(): Boolean

}