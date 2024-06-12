package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * for memory limited environments
 * the class nodes (and others) should be treated as read only. visit the mapping tree to make changes.
 */
class LazyMappingTree : AbstractMappingTree() {
    private val _packages = mutableListOf<LazyPackageNode>()
    private val _classes = mutableListOf<LazyClassNode>()
    private val _constantGroups = mutableListOf<LazyConstantGroupNode>()

    val byNamespace = mutableMapOf<Namespace, MutableMap<InternalName, LazyClassNode>>()

    fun getLazyClass(namespace: Namespace, name: InternalName): LazyClassNode? {
        return (byNamespace.getOrPut(namespace) {
            val map = mutableMapOf<InternalName, LazyClassNode>()
            _classes.forEach { c -> c.names[namespace]?.let { map[it] = c } }
            map
        })[name]
    }

    override fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return getLazyClass(namespace, name)?.resolve()
    }

    override fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassNode>> {
        val backing = _classes.iterator()
        return object : Iterator<Pair<Map<Namespace, InternalName>, () -> ClassNode>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Map<Namespace, InternalName>, () -> ClassNode> {
                return backing.next().let { it.names to it::resolve }
            }

        }
    }

    override fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageNode>> {
        val backing = _packages.iterator()
        return object : Iterator<Pair<Map<Namespace, PackageName>, () -> PackageNode>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Map<Namespace, PackageName>, () -> PackageNode> {
                return backing.next().let { it.names to it::resolve }
            }

        }
    }

    override fun constantGroupsIter(): Iterator<Pair<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode>> {
        val backing = _constantGroups.iterator()
        return object : Iterator<Pair<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode>> {
            override fun hasNext(): Boolean {
                return backing.hasNext()
            }

            override fun next(): Pair<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode> {
                val node = backing.next()
                return Triple(node.name, node.type, listOf(node.baseNs) + node.namespaces) to node::resolve
            }

        }
    }

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _classes[index]
                return Triple(node.names, node::resolve, node::accept)
            }

        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _packages[index]
                return Triple(node.names, node::resolve, node::accept)
            }

        }
    }

    override fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int
                get() = _constantGroups.size

            override fun get(index: Int): Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val node = _constantGroups[index]
                return Triple(Triple(node.name, node.type, listOf(node.baseNs) + node.namespaces), node::resolve, node::accept)
            }

        }
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
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
        return node.visitPackage(names)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = getLazyClass(ns, names[ns]!!)
            if (existing != null) {
                // add other names
                for (ns in names.keys) {
                    byNamespace[ns]?.remove(existing.names[ns])
                    byNamespace[ns]?.put(names[ns]!!, existing)
                }
                mergeNs(names.keys)
                return existing.visitClass(names)
            }
        }
        val node = LazyClassNode(this)
        for (ns in names.keys) {
            byNamespace[ns]?.put(names[ns]!!, node)
        }
        _classes.add(node)
        return node.visitClass(names)
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val node = LazyConstantGroupNode(this, type, name, baseNs, namespaces)
        _constantGroups.add(node)
        return node.visitConstantGroup(type, name, baseNs, namespaces)
    }

    override fun acceptInner(visitor: MappingVisitor, nsFilter: Collection<Namespace>, minimize: Boolean) {
        if (minimize) {
            super.acceptInner(visitor, nsFilter, minimize)
        } else {
            visitor.visitHeader(*namespaces.map { it.name }.toTypedArray())
            for (ext in extensions) {
                ext.value.accept(visitor, nsFilter, minimize)
            }
            val nsFilterSet = nsFilter.toSet()
            for (node in _packages) {
                node.accept(visitor, nsFilterSet)
            }
            for (node in _classes) {
                node.accept(visitor, nsFilterSet)
            }
            for (node in _constantGroups) {
                node.accept(visitor, nsFilterSet)
            }
        }
    }

    class LazyClassNode(val tree: LazyMappingTree) {
        var _names: MutableMap<Namespace, InternalName> = mutableMapOf()
        val names: Map<Namespace, InternalName> get() = _names
        var value: String = ""

        fun append(value: String) {
            this.value += value
        }

        fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor {
            tree.mergeNs(names.keys)
            this._names.putAll(names)
            return UMFWriter.UMFClassWriter(::append, tree.namespaces.toList())
        }

        fun resolve(): ClassNode {
            return ClassNode(tree).also { node ->
                node.setNames(names)
                accept(object: ThrowingVisitor() {
                    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: MappingVisitor, nsFilter: Collection<Namespace>) {
            val cls = visitor.visitClass(this.names)
            if (cls != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    CharReader(value),
                    null,
                    ThrowingVisitor(),
                    emptyMap(),
                    mutableListOf(cls),
                    mutableListOf(-1, 0),
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

        fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
            tree.mergeNs(names.keys)
            this._names.putAll(names)
            return UMFWriter.UMFPackageWriter(::append, tree.namespaces.toList())
        }

        fun resolve(): PackageNode {
            return PackageNode(tree).also { node ->
                node.setNames(names)
                accept(object: ThrowingVisitor() {
                    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: MappingVisitor, nsFilter: Collection<Namespace>) {
            val pkg = visitor.visitPackage(this.names)
            if (pkg != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    CharReader(value),
                    null,
                    ThrowingVisitor(),
                    emptyMap(),
                    mutableListOf(pkg),
                    mutableListOf(-1, 0),
                    tree.namespaces::get
                )
            }
            pkg?.visitEnd()
        }
    }

    class LazyConstantGroupNode(val tree: LazyMappingTree, val type: ConstantGroupNode.InlineType, val name: String?, val baseNs: Namespace, val namespaces: Set<Namespace>) {
        var value: String = ""

        fun append(value: String) {
            this.value += value
        }

        fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            name: String?,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor {
            return UMFWriter.UMFConstantGroupWriter(::append, namespaces.toList())
        }

        fun resolve(): ConstantGroupNode {
            return ConstantGroupNode(tree, type, name, baseNs).also { node ->
                node.addNamespaces(namespaces)
                accept(object: ThrowingVisitor() {
                    override fun visitConstantGroup(
                        type: ConstantGroupNode.InlineType,
                        name: String?,
                        baseNs: Namespace,
                        namespaces: Set<Namespace>
                    ): ConstantGroupVisitor {
                        return node
                    }
                }, tree.namespaces.toSet())
            }
        }

        fun accept(visitor: MappingVisitor, nsFilter: Collection<Namespace>) {
            val cgn = visitor.visitConstantGroup(type, name, baseNs, namespaces.filter { it in nsFilter }.toSet())
            if (cgn != null) {
                UMFReader.readWithStack(
                    EnvType.JOINED,
                    CharReader(value),
                    null,
                    ThrowingVisitor(),
                    emptyMap(),
                    mutableListOf(cgn),
                    mutableListOf(-1, 0),
                    tree.namespaces::get
                )
            }
            cgn?.visitEnd()
        }
    }
}