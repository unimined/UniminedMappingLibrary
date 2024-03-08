package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node.PackageNode
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.*

class MappingTree : AbstractMappingTree() {
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

    override fun constantGroupsIter(): Iterator<ConstantGroupNode> = _constantGroups.iterator()

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = _classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val cls = _classes[index]
                return Triple(cls.names.filterNotNullValues(), { cls }, { visitor, nsFilter -> cls.accept(visitor, nsFilter, false) })
            }
        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {

            override val size: Int get() = _packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val pkg = _packages[index]
                return Triple(pkg.names.filterNotNullValues(), { pkg }, { visitor, nsFilter -> pkg.accept(visitor, nsFilter, false) })
            }

        }
    }

    override fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = _constantGroups.size

            override fun get(index: Int): Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val group = _constantGroups[index]
                return Triple(Triple(null as String?, group.type, listOf(group.baseNs) + group.namespaces.toList()), { group }, { visitor, nsFilter -> group.accept(visitor, nsFilter, false) })
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
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val node = ConstantGroupNode(this, type, baseNs)
        node.addNamespaces(namespaces)
        _constantGroups.add(node)
        return node
    }

}