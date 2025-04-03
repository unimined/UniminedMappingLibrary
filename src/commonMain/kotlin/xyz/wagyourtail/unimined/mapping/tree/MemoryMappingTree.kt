package xyz.wagyourtail.unimined.mapping.tree

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.utils.coroutines.parallelMap
import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.PackageVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.*

class MemoryMappingTree : AbstractMappingTree() {
    private val _namespaces = mutableListOf<Namespace>()
    private val _packages = mutableListOf<PackageNode>()
    private val _classes = mutableListOf<ClassNode>()
    private val _constantGroups = mutableListOf<ConstantGroupNode>()

    internal val packages: List<PackageNode> get() = _packages
    internal val classes: List<ClassNode> get() = _classes
    internal val constantGroups: List<ConstantGroupNode> get() = _constantGroups


    private val byNamespace = defaultedMapOf<Namespace, MutableMap<InternalName, ClassNode>> { mutableMapOf() }

    override fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return byNamespace[namespace][name]
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

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor {
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
                for ((ns, name) in existing.names.filter { it.key in names && it.value != names[it.key] }) {
                    byNamespace[ns].remove(name)
                }
                // add other names
                existing.setNames(names)
                for ((ns, name) in names) {
                    byNamespace[ns].put(name, existing)
                }
                return existing
            }
        }
        val node = ClassNode(this)
        node.setNames(names)
        for ((ns, name) in names) {
            byNamespace[ns].put(name, node)
        }
        _classes.add(node)
        return node
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor {
        val node = ConstantGroupNode(this, type, name, baseNs)
        node.addNamespaces(namespaces)
        _constantGroups.add(node)
        return node
    }

    /**
     * function to make [LazyResolvables] resolve in parallel
     */
    suspend fun resolveLazyResolvables() {
        classes.parallelMap {
            coroutineScope {
                listOf(
                    async {
                        it.fields.resolve()
                    },
                    async {
                        it.methods.resolve()
                    },
                    async {
                        it.wildcards.resolve()
                    }
                ).awaitAll()
            }
        }
    }

    /**
     * function to fill missing names
     */
    suspend fun fillMissingNames(vararg toFill: Pair<Namespace, Set<Namespace>>) {
        coroutineScope {
            listOf(
                async {
                    packages.parallelMap {
                        val nameMap = it.names.toMutableMap()
                        NameCopyDelegate.fillAllNames(toFill, nameMap)
                        it.setNames(nameMap)
                    }
                },
                async {
                    classes.parallelMap {
                        val nameMap = it.names.toMutableMap()
                        NameCopyDelegate.fillAllNames(toFill, nameMap)
                        it.setNames(nameMap)
                        for (fill in toFill) {
                            it.acceptInner(
                                DelegateClassVisitor(it, NameCopyDelegate(fill)),
                                namespaces,
                                false
                            )
                        }
                    }
                }
            ).awaitAll()
        }
    }

}