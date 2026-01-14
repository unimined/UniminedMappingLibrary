package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._constant.ConstantGroupMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._package.PackageMappingImpl
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateConstantGroupMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegatePackageMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NamespaceRecordingDelegate
import xyz.wagyourtail.unimined.mapping.visitor.delegate.copyDescriptors

/**
 * for memory limited environments
 * the class nodes (and others) should be treated as read only. visit the mapping tree to make changes.
 */
class LazyMappingTree : AbstractMappingTree() {
    private val _packages = mutableListOf<LazyPackageNode>()
    private val _classes = mutableListOf<LazyClassNode>()
    private val _constantGroups = mutableListOf<LazyConstantGroupNode>()

    val byNamespace = defaultedMapOf<Namespace, MutableMap<InternalName, LazyClassNode>> { mutableMapOf() }

    fun getLazyClass(namespace: Namespace, name: InternalName): LazyClassNode? {
        return byNamespace[namespace][name]
    }

    override fun getClass(namespace: Namespace, name: InternalName): ClassMappingImpl? {
        return getLazyClass(namespace, name)?.resolve()
    }

    override fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassMappingImpl>> {
        val backing = _classes.iterator()
        return object : Iterator<Pair<Map<Namespace, InternalName>, () -> ClassMappingImpl>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Map<Namespace, InternalName>, () -> ClassMappingImpl> {
                return backing.next().let { it.names to it::resolve }
            }

        }
    }

    override fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageMappingImpl>> {
        val backing = _packages.iterator()
        return object : Iterator<Pair<Map<Namespace, PackageName>, () -> PackageMappingImpl>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Map<Namespace, PackageName>, () -> PackageMappingImpl> {
                return backing.next().let { it.names to it::resolve }
            }

        }
    }

    override fun constantGroupsIter(): Iterator<Pair<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl>> {
        val backing = _constantGroups.iterator()
        return object : Iterator<Pair<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl> {
                val node = backing.next()
                return Pair(node.name, node.type) to node::resolve
            }

        }
    }

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _classes[index]
                return Triple(node.names, node::resolve, node::accept)
            }

        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _packages[index]
                return Triple(node.names, node::resolve, node::accept)
            }

        }
    }

    override fun constantGroupList(): List<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _constantGroups.size

            override fun get(index: Int): Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _constantGroups[index]
                return Triple(Pair(node.name, node.type), node::resolve, node::accept)
            }

        }
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = _packages.firstOrNull { it.names[ns] == names[ns] }
            if (existing != null) {
                // add other names
                mergeNs(names.keys)
                return existing.visitPackage(names)
            }
        }
        val node = LazyPackageNode(this)
        _packages.add(node)
        mergeNs(names.keys)
        return node.visitPackage(names)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = getLazyClass(ns, names[ns]!!)
            if (existing != null) {
                for ((ns, name) in existing.names.filter { it.key in names && it.value != names[it.key] }) {
                    byNamespace[ns].remove(name)
                }
                // add other names
                existing.setNames(names)
                for ((ns, name) in names) {
                    byNamespace[ns].put(name, existing)
                }
                return existing.visitClass(names)
            }
        }
        val node = LazyClassNode(this)
        node.setNames(names)
        for ((ns, name) in names) {
            byNamespace[ns].put(name, node)
        }
        _classes.add(node)
        return node.visitClass(names)
    }

    override fun visitConstantGroup(
        type: InlineType,
        name: String?,
        baseNs: Namespace,
    ): ConstantGroupMappingVisitor {
        val node = LazyConstantGroupNode(this, type, name, baseNs)
        _constantGroups.add(node)
        mergeNs(setOf(baseNs) + namespaces)
        return node.visitConstantGroup(type, name, baseNs)
    }

    fun nonLazyAccept(visitor: RootMappingVisitor, nsFilter: List<Namespace> = namespaces, sort: Boolean = false) {
        return super.accept(visitor, nsFilter, sort)
    }

    override fun accept(visitor: RootMappingVisitor, nsFilter: List<Namespace>, sort: Boolean) {
        return lazyAccept(visitor.copyDescriptors(this), nsFilter)
    }

    fun lazyAccept(visitor: RootMappingVisitor, nsFilter: Collection<Namespace> = namespaces) {
        visitor.visitHeader(*nsFilter.filter { namespaces.contains(it) }.map { it.name }.toTypedArray())
        for (pkg in _packages) {
            pkg.accept(visitor, nsFilter)
        }
        for (cls in _classes) {
            cls.accept(visitor, nsFilter)
        }
        for (group in _constantGroups) {
            group.accept(visitor, nsFilter)
        }
        visitor.visitEnd()
    }

    override fun map(fromNs: Namespace, toNs: Namespace, internalName: InternalName): InternalName {
        checkNamespace(fromNs)
        checkNamespace(toNs)
        if (fromNs == toNs) return internalName
        val cls = getLazyClass(fromNs, internalName)
        if (cls != null) {
            return cls.names[toNs] ?: internalName
        }
        val parts = internalName.getParts()
        val pkg = map(fromNs, toNs, parts.first)
        return InternalName(pkg, parts.second)
    }

    override val packages: Iterable<PackageMapping>
        get() = packagesIter().asSequence().map { it.second() }.asIterable()

    override val classes: List<ClassMapping>
        get() = classesIter().asSequence().map { it.second() }.toList()

    override val constantGroups: List<ConstantGroupMapping>
        get() = constantGroupList().asSequence().map { it.second() }.toList()

    class LazyClassNode(val tree: LazyMappingTree) {
        var _names: MutableMap<Namespace, InternalName> = mutableMapOf()
        val names: Map<Namespace, InternalName> get() = _names
        var value: String = ""

        fun append(value: String) {
            this.value += value
        }

        fun setNames(names: Map<Namespace, InternalName>) {
            tree.mergeNs(names.keys)
            this._names.putAll(names)
        }

        fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor {
            tree.mergeNs(names.keys)
            this._names.putAll(names)
            val delegator = UMFWriter.UMFWriterDelegator(::append, true)
            return DelegateClassMappingVisitor(DelegateClassMappingVisitor(EmptyClassMappingVisitor(), delegator), NamespaceRecordingDelegate {
                tree.mergeNs(it)
                delegator.namespaces = tree.namespaces.toList()
            })
        }

        fun resolve(): ClassMappingImpl {
            return ClassMappingImpl(tree).also { node ->
                node.setNames(names)
                accept(object: ThrowingRootMapping() {
                    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>) {
            val cls = visitor.visitClass(this.names)
            if (cls != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    StringCharReader(value),
                    null,
                    ThrowingRootMapping(),
                    emptyMap(),
                    mutableListOf(cls),
                    mutableListOf(-1, -1),
                    tree.namespaces::get
                )
            }
            cls?.visitEnd()
        }
    }

    class LazyPackageNode(val tree: LazyMappingTree) {
        private val _names: MutableMap<Namespace, PackageName> = mutableMapOf()
        val names: Map<Namespace, PackageName> get() = _names
        var value: String = ""

        fun append(value: String) {
            this.value += value
        }

        fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor {
            tree.mergeNs(names.keys)
            this._names.putAll(names)
            val delegator = UMFWriter.UMFWriterDelegator(::append, true)
            return DelegatePackageMappingVisitor(DelegatePackageMappingVisitor(EmptyPackageMappingVisitor(), delegator), NamespaceRecordingDelegate {
                tree.mergeNs(it)
                delegator.namespaces = tree.namespaces.toList()
            })
        }

        fun resolve(): PackageMappingImpl {
            return PackageMappingImpl(tree).also { node ->
                node.setNames(names)
                accept(object: ThrowingRootMapping() {
                    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>) {
            val pkg = visitor.visitPackage(this.names)
            if (pkg != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    StringCharReader(value),
                    null,
                    ThrowingRootMapping(),
                    emptyMap(),
                    mutableListOf(pkg),
                    mutableListOf(-1, -1),
                    tree.namespaces::get
                )
            }
            pkg?.visitEnd()
        }
    }

    class LazyConstantGroupNode(val tree: LazyMappingTree, val type: InlineType, val name: String?, val baseNs: Namespace) {
        var value: String = ""

        fun append(value: String) {
            this.value += value
        }

        fun visitConstantGroup(
            type: InlineType,
            name: String?,
            baseNs: Namespace,
        ): ConstantGroupMappingVisitor {
            tree.mergeNs(setOf(baseNs))
            val delegator = UMFWriter.UMFWriterDelegator(::append, true)
            delegator.namespaces = tree.namespaces.toList()
            return DelegateConstantGroupMappingVisitor(EmptyConstantGroupMappingVisitor(), delegator)
        }

        fun resolve(): ConstantGroupMappingImpl {
            return ConstantGroupMappingImpl(tree, type, name, baseNs).also { node ->
                accept(object: ThrowingRootMapping() {
                    override fun visitConstantGroup(
                        type: InlineType,
                        name: String?,
                        baseNs: Namespace,
                    ): ConstantGroupMappingVisitor {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: RootMappingVisitor, nsFilter: Collection<Namespace>) {
            val cgn = visitor.visitConstantGroup(type, name, baseNs)
            if (cgn != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    StringCharReader(value),
                    null,
                    ThrowingRootMapping(),
                    emptyMap(),
                    mutableListOf(cgn),
                    mutableListOf(-1, -1),
                    tree.namespaces::get
                )
            }
            cgn?.visitEnd()
        }
    }

    open class ThrowingRootMapping : RootMappingVisitor {

        override fun visitHeader(vararg namespaces: String) {
            throw UnsupportedOperationException()
        }

        override fun visitHeader(vararg namespaces: Namespace) {
            throw UnsupportedOperationException()
        }

        override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor? {
            throw UnsupportedOperationException()
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor? {
            throw UnsupportedOperationException()
        }

        override fun visitConstantGroup(
            type: InlineType,
            name: String?,
            baseNs: Namespace
        ): ConstantGroupMappingVisitor? {
            throw UnsupportedOperationException()
        }

        override fun visitEnd() {
            throw UnsupportedOperationException()
        }
    }
}