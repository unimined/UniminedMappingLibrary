package xyz.wagyourtail.unimined.mapping.tree.mapping._package

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.mapping.AnnotationMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.JavadocMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegatePackageMappingVisitor

class PackageMappingImpl(parent: AbstractMappingTree) : BaseMappingImpl<PackageMappingVisitor, RootMappingVisitor>(parent), PackageMapping, PackageMappingVisitor {
    private val _names: MutableMap<Namespace, PackageName> = mutableMapOf()
    private val _annotations: MutableList<AnnotationMappingImpl<PackageMappingVisitor>> = mutableListOf()
    private var _comments: MutableMap<String, JavadocMappingImpl<PackageMappingVisitor>> = mutableMapOf()

    override val names: Map<Namespace, PackageName> get() = _names
    override val annotations: List<AnnotationMappingImpl<PackageMappingVisitor>> get() = _annotations
    override val javadoc: List<JavadocMappingImpl<PackageMappingVisitor>> get() = _comments.values.toList()

    fun getName(namespace: Namespace) = names[namespace]

    fun setNames(names: Map<Namespace, PackageName>) {
        root.mergeNs(names.keys)
        _names.putAll(names)
    }

    override fun acceptOuter(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>): PackageMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitPackage(names)
    }

    override fun acceptInner(visitor: PackageMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        for (annotation in if (sort) annotations.sortedBy { it.toString() } else annotations) {
            annotation.accept(visitor, nsFilter, sort)
        }
        super.acceptInner(visitor, nsFilter, sort)
    }

    override fun visitAnnotation(
        type: AddRemoveModify,
        baseNs: Namespace,
        annotation: Annotation,
    ): AnnotationMappingVisitor {
        val node = AnnotationMappingImpl(this, type, baseNs, annotation)
        _annotations.add(node)
        return node
    }

    override fun visitJavadoc(value: String, baseNs: Namespace): JavadocMappingVisitor {
        val node = _comments.getOrPut(value) { JavadocMappingImpl(this, value, baseNs) }
        return node
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitPackage(EmptyRootMappingVisitor(), names)
        if (inner) acceptInner(DelegatePackageMappingVisitor(EmptyPackageMappingVisitor(), delegator), root.namespaces, true)
    }

}