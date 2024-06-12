package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.*

class MemoryMappingTree : AbstractMappingTree() {
    private val _namespaces = mutableListOf<Namespace>()
    private val _packages = mutableListOf<PackageNode>()
    private val _classes = mutableListOf<ClassNode>()
    private val _constantGroups = mutableListOf<ConstantGroupNode>()

    internal val packages: List<PackageNode> get() = _packages
    internal val classes: List<ClassNode> get() = _classes
    internal val constantGroups: List<ConstantGroupNode> get() = _constantGroups


    val byNamespace = mutableMapOf<Namespace, MutableMap<InternalName, ClassNode>>()

    override fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return (byNamespace.getOrPut(namespace) {
            val map = mutableMapOf<InternalName, ClassNode>()
            _classes.forEach { c -> c.getName(namespace)?.let { map[it] = c } }
            map
        })[name]
    }

    override fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassNode>> = _classes.iterator().asSequence().map {
        it.names.filterNotNullValues() to { it }
    }.iterator()

    override fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageNode>> = _packages.iterator().asSequence().map {
        it.names.filterNotNullValues() to { it }
    }.iterator()

    override fun constantGroupsIter(): Iterator<Pair<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode>> = _constantGroups.iterator().asSequence().map {
        Triple(it.name, it.type, listOf(it.baseNs, *it.namespaces.toTypedArray())) to { it }
    }.iterator()

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor) -> Unit>>() {
            override val size: Int get() = _classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor) -> Unit> {
                val cls = _classes[index]
                return Triple(cls.names.filterNotNullValues(), { cls }, { visitor -> cls.accept(visitor, false) })
            }
        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor) -> Unit>>() {

            override val size: Int get() = _packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor) -> Unit> {
                val pkg = _packages[index]
                return Triple(pkg.names.filterNotNullValues(), { pkg }, { visitor -> pkg.accept(visitor, false) })
            }

        }
    }

    override fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor) -> Unit>> {
        return object : AbstractList<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor) -> Unit>>() {
            override val size: Int get() = _constantGroups.size

            override fun get(index: Int): Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor) -> Unit> {
                val group = _constantGroups[index]
                return Triple(Triple(null as String?, group.type, listOf(group.baseNs) + group.namespaces.toList()), { group }, { visitor -> group.accept(visitor, false) })
            }
        }
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = packages.firstOrNull { it.names[ns] == names[ns] }
            if (existing != null) {
                // add other names
                existing.setNames(names)
                return existing
            }
        }
        val node = PackageNode(this)
        node.setNames(names)
        _packages.add(node)
        return node
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassNode {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = getClass(ns, names[ns]!!)
            if (existing != null) {
                // add other names
                for (ns in names.keys) {
                    byNamespace[ns]?.remove(existing.getName(ns))
                    byNamespace[ns]?.put(names[ns]!!, existing)
                }
                existing.setNames(names)
                return existing
            }
        }
        val node = ClassNode(this)
        for (ns in names.keys) {
            byNamespace[ns]?.put(names[ns]!!, node)
        }
        node.setNames(names)
        _classes.add(node)
        return node
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val node = ConstantGroupNode(this, type, name, baseNs)
        node.addNamespaces(namespaces)
        _constantGroups.add(node)
        return node
    }

}