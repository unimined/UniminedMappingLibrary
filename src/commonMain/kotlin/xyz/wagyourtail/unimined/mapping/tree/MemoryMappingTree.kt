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
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._constant.ConstantGroupMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._package.PackageMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.*

class MemoryMappingTree : AbstractMappingTree() {
    private val _namespaces = mutableListOf<Namespace>()
    private val _packages = mutableListOf<PackageMappingImpl>()
    private val _classes = mutableListOf<ClassMappingImpl>()
    private val _constantGroups = mutableListOf<ConstantGroupMappingImpl>()

    override val packages: List<PackageMappingImpl> get() = _packages
    override val classes: List<ClassMappingImpl> get() = _classes
    override val constantGroups: List<ConstantGroupMappingImpl> get() = _constantGroups


    private val byNamespace = defaultedMapOf<Namespace, MutableMap<InternalName, ClassMappingImpl>> { mutableMapOf() }

    override fun getClass(namespace: Namespace, name: InternalName): ClassMappingImpl? {
        return byNamespace[namespace][name]
    }

    override fun classesIter(): Iterator<Pair<Map<Namespace, InternalName>, () -> ClassMappingImpl>> = _classes.iterator().asSequence().map {
        it.names.filterNotNullValues() to { it }
    }.iterator()

    override fun packagesIter(): Iterator<Pair<Map<Namespace, PackageName>, () -> PackageMappingImpl>> = _packages.iterator().asSequence().map {
        it.names.filterNotNullValues() to { it }
    }.iterator()

    override fun constantGroupsIter(): Iterator<Pair<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl>> = _constantGroups.iterator().asSequence().map {
        Pair(it.name, it.type) to { it }
    }.iterator()

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = _classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val cls = _classes[index]
                return Triple(cls.names.filterNotNullValues(), { cls }, { visitor, nsFilter -> cls.accept(visitor, nsFilter, false) })
            }
        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {

            override val size: Int get() = _packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val pkg = _packages[index]
                return Triple(pkg.names.filterNotNullValues(), { pkg }, { visitor, nsFilter -> pkg.accept(visitor, nsFilter, false) })
            }

        }
    }

    override fun constantGroupList(): List<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        return object : AbstractList<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = _constantGroups.size

            override fun get(index: Int): Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit> {
                val group = _constantGroups[index]
                return Triple(Pair(group.name, group.type), { group }, { visitor, nsFilter -> group.accept(visitor, nsFilter, false) })
            }
        }
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor {
        for (ns in namespaces.filter { it in names }) {
            // check if exists
            val existing = packages.firstOrNull { it.names[ns] == names[ns] }
            if (existing != null) {
                // add other names
                existing.setNames(names)
                return existing
            }
        }
        val node = PackageMappingImpl(this)
        node.setNames(names)
        _packages.add(node)
        return node
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingImpl {
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
        val node = ClassMappingImpl(this)
        node.setNames(names)
        for ((ns, name) in names) {
            byNamespace[ns].put(name, node)
        }
        _classes.add(node)
        return node
    }

    override fun visitConstantGroup(
        type: InlineType,
        name: String?,
        baseNs: Namespace,
    ): ConstantGroupMappingVisitor {
        val node = ConstantGroupMappingImpl(this, type, name, baseNs)
        _constantGroups.add(node)
        return node
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
                                DelegateClassMappingVisitor(it, NameCopyDelegate(fill)),
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