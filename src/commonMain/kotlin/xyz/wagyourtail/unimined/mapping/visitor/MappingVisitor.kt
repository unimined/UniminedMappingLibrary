package xyz.wagyourtail.unimined.mapping.visitor

import okio.Closeable
import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView

/**
 * This visitor is designed to be implemented as a visitor with a stack of elements...
 * so basically, when you visit a element, it gets pushed to the stack so you can visit it's children when it returns true.
 * visitEnd then pops the element from the stack.
 */
abstract class MappingVisitor(val root: ElementType? = null): Closeable {

    abstract fun visitNamespaces(vararg namespaces: String)

    abstract fun visitClass(names: Map<String, String?>): Boolean

    abstract fun visitMethod(names: Map<String, String?>): Boolean

    abstract fun visitField(names: Map<String, String?>): Boolean

    abstract fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): Boolean

    abstract fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): Boolean

    abstract fun visitInnerClass(
        type: InnerClassPropertyView.InnerType,
        names: Map<String, String?>
    ): Boolean

    abstract fun visitSignature(names: Map<String, String?>): Boolean

    abstract fun visitComment(names: Map<String, String?>): Boolean

    abstract fun visitAnnotation(
        action: AnnotationPropertyView.Action,
        key: String,
        value: String?,
        vararg namespaces: String
    ): Boolean

    abstract fun visitAccess(action: AccessPropertyView.Action, access: AccessFlag, vararg namespaces: String): Boolean

    abstract fun visitExtension(key: String, vararg values: String?): Boolean

    abstract fun visitEnd()

}