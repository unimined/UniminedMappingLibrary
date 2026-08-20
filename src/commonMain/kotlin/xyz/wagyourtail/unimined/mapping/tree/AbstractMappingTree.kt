package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._constant.ConstantGroupMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._package.PackageMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.InlineType
import xyz.wagyourtail.unimined.mapping.visitor.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.RootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.NullMappingVisitor

abstract class AbstractMappingTree : BaseMappingImpl<RootMappingVisitor, NullMappingVisitor>(null), MappingTree, RootMappingVisitor {
    private val _namespaces = mutableListOf<Namespace>()
    override val namespaces: List<Namespace> get() = _namespaces

    internal fun mergeNs(names: Iterable<Namespace>) {
        for (ns in names) {
            if (ns !in _namespaces) {
                _namespaces.add(ns)
            }
        }
    }

    fun visitClass(vararg names: Pair<Namespace, InternalName>) = visitClass(names.toMap())

    open fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassMappingImpl>> {
        return classList().asSequence().map { (names, cls, _) -> names to cls }.iterator()
    }

    open fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageMappingImpl>> {
        return packageList().asSequence().map { (names, pkg, _) -> names to pkg }.iterator()
    }

    open fun constantGroupsIter(): Iterator<Pair<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl>> {
        return constantGroupList().asSequence().map { (names, group, _) -> names to group }.iterator()
    }

    abstract fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>

    abstract fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>

    abstract fun constantGroupList(): List<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>

    override fun visitHeader(vararg namespaces: String) {
        mergeNs(namespaces.map { Namespace(it) }.toSet())
    }

    override fun visitHeader(vararg namespaces: Namespace) {
        mergeNs(namespaces.toSet())
    }

    open fun accept(visitor: RootMappingVisitor, nsFilter: List<Namespace> = namespaces, sort: Boolean = false) {
        acceptInner(visitor, nsFilter, sort)
        visitor.visitEnd()
    }

    override fun acceptOuter(visitor: NullMappingVisitor, nsFilter: Collection<Namespace>): RootMappingVisitor? {
        return null
    }

    override fun acceptInner(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>, sort: Boolean) {
        visitor.visitHeader(*nsFilter.filter { namespaces.contains(it) }.map { it.name }.toTypedArray())
        super.acceptInner(visitor, nsFilter, sort)
        val packageIter = packagesIter().asSequence().map { it.second() }
        for (pkg in if (sort) packageIter.sortedBy { it.toString() } else packageIter) {
            pkg.accept(visitor, nsFilter, sort)
        }
        val clsIter = classesIter().asSequence().map { it.second() }
        for (cls in if (sort) clsIter.sortedBy { it.toString() } else clsIter) {
            cls.accept(visitor, nsFilter, sort)
        }
        val cgIter = constantGroupsIter().asSequence().map { it.second() }
        for (group in if (sort) cgIter.sortedBy { it.toString() } else cgIter) {
            group.accept(visitor, nsFilter, sort)
        }
    }

    override fun toUMF(inner: Boolean) = buildString {
        acceptInner(UMFWriter.write(::append, false), namespaces, true)
    }

}