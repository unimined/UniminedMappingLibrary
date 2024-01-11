package xyz.wagyourtail.unimined.mapping.visitor.impl

import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

open class DelegateVisitor(val delegate: MappingVisitor): MappingVisitor(delegate.root) {
    override fun visitNamespaces(vararg namespaces: String) {
        delegate.visitNamespaces(*namespaces)
    }

    override fun visitClass(names: Map<String, String?>): Boolean {
        return delegate.visitClass(names)
    }

    override fun visitMethod(names: Map<String, String?>): Boolean {
        return delegate.visitMethod(names)
    }

    override fun visitField(names: Map<String, String?>): Boolean {
        return delegate.visitField(names)
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): Boolean {
        return delegate.visitParameter(index, lvOrd, names)
    }

    override fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): Boolean {
        return delegate.visitVariable(lvOrd, startOp, names)
    }

    override fun visitInnerClass(
        type: InnerClassPropertyView.InnerType,
        names: Map<String, String?>
    ): Boolean {
        return delegate.visitInnerClass(type, names)
    }

    override fun visitSignature(names: Map<String, String?>): Boolean {
        return delegate.visitSignature(names)
    }

    override fun visitComment(names: Map<String, String?>): Boolean {
        return delegate.visitComment(names)
    }

    override fun visitAnnotation(
        action: AnnotationPropertyView.Action,
        key: String,
        value: String?,
        vararg namespaces: String
    ): Boolean {
        return delegate.visitAnnotation(action, key, value, *namespaces)
    }

    override fun visitAccess(
        action: AccessPropertyView.Action,
        access: AccessFlag,
        vararg namespaces: String
    ): Boolean {
        return delegate.visitAccess(action, access, *namespaces)
    }

    override fun visitExtension(key: String, vararg values: String?): Boolean {
        return delegate.visitExtension(key, *values)
    }

    override fun visitEnd() {
        delegate.visitEnd()
    }

    override fun close() {
        delegate.close()
    }

}