package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.tree.mapping.AccessParentMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.AnnotationMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.JavadocMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*

abstract class MemberMappingImpl<T: MemberMappingVisitor, U: BaseMappingVisitor>(parent: BaseMappingImpl<U, *>) : AccessParentMappingImpl<T, U>(parent), MemberMapping, MemberMappingVisitor {
    private var _javadoc: MutableMap<String, JavadocMappingImpl<T>> = mutableMapOf()
    private val _annotations: MutableList<AnnotationMappingImpl<T>> = mutableListOf()

    override val javadoc: List<JavadocMappingImpl<T>> get() = _javadoc.values.toList()
    override val annotations: List<AnnotationMappingImpl<T>> get() = _annotations

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor? {
        val node = _javadoc.getOrPut(value) { JavadocMappingImpl(this, value, baseNs) }
        return node
    }

    override fun visitAnnotation(
        type: AnnotationType,
        baseNs: Namespace,
        annotation: Annotation
    ): AnnotationMappingVisitor? {
        val node = AnnotationMappingImpl(this, type, baseNs, annotation)
        _annotations.add(node)
        return node
    }

    override fun acceptInner(visitor: T, nsFilter: Collection<Namespace>, sort: Boolean) {
        for (annotation in if (sort) annotations.sortedBy { it.toString() } else annotations) {
            annotation.accept(visitor, nsFilter, sort)
        }
        for (comment in if (sort) javadoc.sortedBy { it.toString() } else javadoc) {
            comment.accept(visitor, nsFilter, sort)
        }
        super.acceptInner(visitor, nsFilter, sort)
    }

}