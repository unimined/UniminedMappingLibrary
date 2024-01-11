package xyz.wagyourtail.unimined.mapping.visitor.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

class CheckedVisitor(val delegate: MappingVisitor): MappingVisitor(delegate.root) {

    companion object {
        val allowedChildren = mapOf(
            null to setOf(
                ElementType.NAMESPACES,
                ElementType.CLASS,
                ElementType.EXTENSION
            ),
            ElementType.CLASS to setOf(
                ElementType.METHOD,
                ElementType.FIELD,
                ElementType.INNER_CLASS,
                ElementType.SIGNATURE,
                ElementType.COMMENT,
                ElementType.ANNOTATION,
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.METHOD to setOf(
                ElementType.PARAMETER,
                ElementType.VARIABLE,
                ElementType.SIGNATURE,
                ElementType.COMMENT,
                ElementType.ANNOTATION,
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.FIELD to setOf(
                ElementType.SIGNATURE,
                ElementType.COMMENT,
                ElementType.ANNOTATION,
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.PARAMETER to setOf(
                ElementType.SIGNATURE,
                ElementType.COMMENT,
                ElementType.ANNOTATION,
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.VARIABLE to setOf(
                ElementType.SIGNATURE,
                ElementType.COMMENT,
                ElementType.ANNOTATION,
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.INNER_CLASS to setOf(
                ElementType.ACCESS,
                ElementType.EXTENSION
            ),
            ElementType.SIGNATURE to setOf(
                ElementType.EXTENSION
            ),
            ElementType.COMMENT to setOf(
                ElementType.EXTENSION
            ),
            ElementType.ANNOTATION to setOf(
                ElementType.EXTENSION
            ),
            ElementType.ACCESS to setOf(
                ElementType.EXTENSION
            ),
            ElementType.EXTENSION to setOf(
                ElementType.EXTENSION
            )
        )
    }

    var endedRoot = false
    var closed = false
    val contentStackTracker = mutableListOf<ElementType>()

    fun checkEnter(next: ElementType) {
        checkClosed()
        checkEndedRoot()
        if (contentStackTracker.isEmpty()) {
            if (next !in allowedChildren[root]!!) {
                throw IllegalStateException("Cannot enter $next from top level")
            }
        }
        if (next !in allowedChildren[contentStackTracker.last()]!!) {
            throw IllegalStateException("Cannot enter $next from ${contentStackTracker.last()}")
        }
    }

    fun checkClosed() {
        if (closed) {
            throw IllegalStateException("Cannot visit after close")
        }
    }

    fun checkEndedRoot() {
        if (endedRoot) {
            throw IllegalStateException("Cannot visit after root end")
        }
    }

    override fun visitNamespaces(vararg namespaces: String) {
        checkEnter(ElementType.NAMESPACES)
        delegate.visitNamespaces(*namespaces)
    }

    override fun visitClass(names: Map<String, String?>): Boolean {
        checkEnter(ElementType.CLASS)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            JVMS.parseFieldDescriptor("L$intlName;")
        }
        val visit = delegate.visitClass(names)
        if (visit) contentStackTracker.add(ElementType.CLASS)
        return visit
    }

    override fun visitMethod(names: Map<String, String?>): Boolean {
        checkEnter(ElementType.METHOD)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            val name = intlName.substringBefore(';')
            JVMS.checkMethodName(name)
            if (name.contains(';')) {
                val desc = intlName.substringAfter(';')
                JVMS.parseMethodDescriptor(desc)
            }
        }
        val visit = delegate.visitMethod(names)
        if (visit) contentStackTracker.add(ElementType.METHOD)
        return visit
    }

    override fun visitField(names: Map<String, String?>): Boolean {
        checkEnter(ElementType.FIELD)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            val name = intlName.substringBefore(';')
            JVMS.checkUnqualifiedName(name)
            if (name.contains(';')) {
                val desc = intlName.substringAfter(';')
                JVMS.parseFieldDescriptor(desc)
            }
        }
        val visit = delegate.visitField(names)
        if (visit) contentStackTracker.add(ElementType.FIELD)
        return visit
    }

    override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): Boolean {
        checkEnter(ElementType.PARAMETER)
        if (index == null && lvOrd == null) {
            throw IllegalArgumentException("Must specify either index or lvOrd")
        }
        for ((_, intlName) in names) {
            if (intlName == null) continue
            JVMS.checkUnqualifiedName(intlName)
        }
        val visit = delegate.visitParameter(index, lvOrd, names)
        if (visit) contentStackTracker.add(ElementType.PARAMETER)
        return visit
    }

    override fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): Boolean {
        checkEnter(ElementType.VARIABLE)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            JVMS.checkUnqualifiedName(intlName)
        }
        val visit = delegate.visitVariable(lvOrd, startOp, names)
        if (visit) contentStackTracker.add(ElementType.VARIABLE)
        return visit
    }

    override fun visitInnerClass(
        type: InnerClassPropertyView.InnerType,
        names: Map<String, String?>
    ): Boolean {
        checkEnter(ElementType.INNER_CLASS)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            val name = intlName.substringBefore(';')
            JVMS.checkUnqualifiedName(name)
            if (name.contains(';')) {
                val desc = intlName.substringAfter(';')
                JVMS.parseFieldSignature(desc)
            }
        }
        val visit = delegate.visitInnerClass(type, names)
        if (visit) contentStackTracker.add(ElementType.INNER_CLASS)
        return visit
    }

    override fun visitSignature(names: Map<String, String?>): Boolean {
        val prev = contentStackTracker.lastOrNull()
        checkEnter(ElementType.SIGNATURE)
        for ((_, intlName) in names) {
            if (intlName == null) continue
            when (prev) {
                ElementType.CLASS -> {
                    JVMS.parseClassSignature(intlName)
                }

                ElementType.METHOD -> {
                    JVMS.parseMethodDescriptor(intlName)
                }

                ElementType.FIELD -> {
                    JVMS.parseFieldDescriptor(intlName)
                }

                ElementType.PARAMETER -> {
                    JVMS.parseFieldDescriptor(intlName)
                }

                ElementType.VARIABLE -> {
                    JVMS.parseFieldDescriptor(intlName)
                }

                else -> {
                    throw IllegalStateException("Cannot enter signature from $prev")
                }
            }
        }
        val visit = delegate.visitSignature(names)
        if (visit) contentStackTracker.add(ElementType.SIGNATURE)
        return visit
    }

    override fun visitComment(names: Map<String, String?>): Boolean {
        checkEnter(ElementType.COMMENT)
        val visit = delegate.visitComment(names)
        if (visit) contentStackTracker.add(ElementType.COMMENT)
        return visit
    }

    override fun visitAnnotation(
        action: AnnotationPropertyView.Action,
        key: String,
        value: String?,
        vararg namespaces: String
    ): Boolean {
        checkEnter(ElementType.ANNOTATION)
        when (action) {
            AnnotationPropertyView.Action.ADD -> {
                Annotation.read("@$key$value")
            }

            AnnotationPropertyView.Action.REMOVE -> {
                JVMS.parseFieldDescriptor(key)
                if (value != null) {
                    throw IllegalArgumentException("Cannot specify value when removing annotation")
                }
            }

            AnnotationPropertyView.Action.MODIFY -> {
                Annotation.read("@$key$value")
            }
        }
        val visit = delegate.visitAnnotation(action, key, value, *namespaces)
        if (visit) contentStackTracker.add(ElementType.ANNOTATION)
        return visit
    }

    override fun visitAccess(
        action: AccessPropertyView.Action,
        access: AccessFlag,
        vararg namespaces: String
    ): Boolean {
        checkEnter(ElementType.ACCESS)
        if (contentStackTracker.last() !in access.elements) {
            throw IllegalStateException("access $access cannot be applied to ${contentStackTracker.last()}")
        }
        val visit = delegate.visitAccess(action, access, *namespaces)
        if (visit) contentStackTracker.add(ElementType.ACCESS)
        return visit
    }

    override fun visitExtension(key: String, vararg values: String?): Boolean {
        checkEnter(ElementType.EXTENSION)
        val visit = delegate.visitExtension(key, *values)
        if (visit) contentStackTracker.add(ElementType.EXTENSION)
        return visit
    }

    override fun visitEnd() {
        if (contentStackTracker.isEmpty()) {
            if (endedRoot) {
                throw IllegalStateException("Cannot end visitor when already closed")
            }
            endedRoot = true
        }
        contentStackTracker.removeLast()
        delegate.visitEnd()
    }

    override fun close() {
        closed = true
        if (contentStackTracker.isNotEmpty()) {
            throw IllegalStateException("Cannot close visitor with unclosed elements: $contentStackTracker")
        }
        delegate.close()
    }

}

fun MappingVisitor.checked(): CheckedVisitor {
    if (this is CheckedVisitor) return this
    return CheckedVisitor(this)
}