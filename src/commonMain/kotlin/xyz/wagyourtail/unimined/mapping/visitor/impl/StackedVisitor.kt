package xyz.wagyourtail.unimined.mapping.visitor.impl

import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView
import xyz.wagyourtail.unimined.mapping.tree.impl.AbstractMappingNode
import xyz.wagyourtail.unimined.mapping.tree.impl.ExtensionMappingNodeFactory
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

abstract class StackedVisitor(val node: AbstractMappingNode<*>) {
    var delegate: StackedVisitor? = null
    private var ended = false

    open fun visitNamespaces(vararg namespaces: String) {
        throw UnsupportedOperationException()
    }

    open fun visitClass(names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitMethod(names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitField(names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitInnerClass(
        type: InnerClassPropertyView.InnerType,
        names: Map<String, String?>
    ): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitSignature(names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitComment(names: Map<String, String?>): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitAnnotation(
        action: AnnotationPropertyView.Action,
        key: String,
        value: String?,
        vararg namespaces: String
    ): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitAccess(
        action: AccessPropertyView.Action,
        access: AccessFlag,
        vararg namespaces: String
    ): StackedVisitor? {
        throw UnsupportedOperationException()
    }

    open fun visitExtension(key: String, vararg values: String?): StackedVisitor? {
        val ext = ExtensionMappingNodeFactory.create(node, key, *values)
        if (ext != null) {
            return ext.asVisitableIntl()
        }
        return null
    }

    open fun visitEnd() {}

    fun asMappingVisitor(): MappingVisitor = object: MappingVisitor(node.type) {
        override fun visitNamespaces(vararg namespaces: String) {
            if (delegate != null && !delegate!!.ended) {
                delegate!!.asMappingVisitor().visitNamespaces(*namespaces)
            } else {
                this@StackedVisitor.visitNamespaces(*namespaces)
            }
            return
        }

        override fun visitClass(names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitClass(names)
            } else {
                delegate = this@StackedVisitor.visitClass(names)
            }
            return delegate != null
        }

        override fun visitMethod(names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitMethod(names)
            } else {
                delegate = this@StackedVisitor.visitMethod(names)
            }
            return delegate != null
        }

        override fun visitField(names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitField(names)
            } else {
                delegate = this@StackedVisitor.visitField(names)
            }
            return delegate != null
        }

        override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitParameter(index, lvOrd, names)
            } else {
                delegate = this@StackedVisitor.visitParameter(index, lvOrd, names)
            }
            return delegate != null
        }

        override fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitVariable(lvOrd, startOp, names)
            } else {
                delegate = this@StackedVisitor.visitVariable(lvOrd, startOp, names)
            }
            return delegate != null
        }

        override fun visitInnerClass(
            type: InnerClassPropertyView.InnerType,
            names: Map<String, String?>
        ): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitInnerClass(type, names)
            } else {
                delegate = this@StackedVisitor.visitInnerClass(type, names)
            }
            return delegate != null
        }

        override fun visitSignature(names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitSignature(names)
            } else {
                delegate = this@StackedVisitor.visitSignature(names)
            }
            return delegate != null
        }

        override fun visitComment(names: Map<String, String?>): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitComment(names)
            } else {
                delegate = this@StackedVisitor.visitComment(names)
            }
            return delegate != null
        }

        override fun visitAnnotation(
            action: AnnotationPropertyView.Action,
            key: String,
            value: String?,
            vararg namespaces: String
        ): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitAnnotation(action, key, value, *namespaces)
            } else {
                delegate = this@StackedVisitor.visitAnnotation(action, key, value, *namespaces)
            }
            return delegate != null
        }

        override fun visitAccess(
            action: AccessPropertyView.Action,
            access: AccessFlag,
            vararg namespaces: String
        ): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitAccess(action, access, *namespaces)
            } else {
                delegate = this@StackedVisitor.visitAccess(action, access, *namespaces)
            }
            return delegate != null
        }

        override fun visitExtension(key: String, vararg values: String?): Boolean {
            if (delegate != null && !delegate!!.ended) {
                return delegate!!.asMappingVisitor().visitExtension(key, *values)
            } else {
                delegate = this@StackedVisitor.visitExtension(key, *values)
            }
            return delegate != null
        }

        override fun visitEnd() {
            if (delegate != null && !delegate!!.ended) {
                delegate!!.asMappingVisitor().visitEnd()
            } else {
                this@StackedVisitor.visitEnd()
                ended = true
            }
        }

        override fun close() {
        }

    }

}