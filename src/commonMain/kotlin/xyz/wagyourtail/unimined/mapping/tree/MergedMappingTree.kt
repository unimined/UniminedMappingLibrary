package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.util.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.MultiClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.MultiConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.MultiPackageVisitor

class MergedMappingTree(val trees: List<AbstractMappingTree>) : AbstractMappingTree() {

    init {
        for (tree in trees) {
            mergeNs(tree.namespaces)
        }
    }

    override fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        val nodes = trees.mapNotNull { it.getClass(namespace, name) }
        if (nodes.isEmpty()) return null
        val cNode = ClassNode(this)
        nodes.forEach {
            cNode.setNames(it.names)
            it.acceptInner(cNode, namespaces, false)
        }
        return cNode
    }

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        val classes = mutableListOf<MergedClassNodeHolder>()
        val byNamespace = defaultedMapOf<Namespace, MutableMap<InternalName, MergedClassNodeHolder>> {
            mutableMapOf()
        }
        for (tree in trees) {
            for ((names, node, acceptor) in tree.classList()) {
                var holder = names.asSequence().map { byNamespace[it.key][it.value] }.filterNotNull().firstOrNull()
                if (holder == null) {
                    holder = MergedClassNodeHolder(names.toMutableMap(), mutableListOf(), mutableListOf())
                    classes.add(holder)
                }
                holder.names.putAll(names)
                holder.nodes.add(node)
                holder.acceptors.add(acceptor)
                for ((ns, name) in names) {
                    byNamespace[ns][name] = holder
                }
            }
        }
        return object : AbstractList<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = classes.size

            override fun get(index: Int): Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val cls = classes[index]
                return Triple(cls.names, cls::toClassNode) { visitor, nsFilter ->
                    cls.acceptors.forEach {
                        it(
                            visitor,
                            nsFilter
                        )
                    }
                }
            }
        }
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        val packages = mutableListOf<MergedPackageNodeHolder>()
        val byNamespace = defaultedMapOf<Namespace, MutableMap<PackageName, MergedPackageNodeHolder>> {
            mutableMapOf()
        }
        for (tree in trees) {
            for ((names, node, acceptor) in tree.packageList()) {
                var holder = names.asSequence().map { byNamespace[it.key][it.value] }.filterNotNull().firstOrNull()
                if (holder == null) {
                    holder = MergedPackageNodeHolder(names.toMutableMap(), mutableListOf(), mutableListOf())
                    packages.add(holder)
                }
                holder.names.putAll(names)
                holder.nodes.add(node)
                holder.acceptors.add(acceptor)
                for ((ns, name) in names) {
                    byNamespace[ns][name] = holder
                }
            }
        }
        return object : AbstractList<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>>() {
            override val size: Int get() = packages.size

            override fun get(index: Int): Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit> {
                val pkg = packages[index]
                return Triple(pkg.names, pkg::toPackageNode) { visitor, nsFilter ->
                    pkg.acceptors.forEach {
                        it(
                            visitor,
                            nsFilter
                        )
                    }
                }
            }
        }
    }

    override fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        val groups = mutableListOf<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>>()
        for (tree in trees) {
            groups.addAll(tree.constantGroupList())
        }
        return groups
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        val visitors = trees.mapNotNull { it.visitPackage(names) }
        if (visitors.isEmpty()) return null
        return MultiPackageVisitor(visitors)
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        val visitors = trees.mapNotNull { it.visitClass(names) }
        if (visitors.isEmpty()) return null
        return MultiClassVisitor(visitors)
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        val visitors = trees.mapNotNull { it.visitConstantGroup(type, name, baseNs, namespaces) }
        if (visitors.isEmpty()) return null
        return MultiConstantGroupVisitor(visitors)
    }

    inner class MergedClassNodeHolder(val names: MutableMap<Namespace, InternalName>, val nodes: MutableList<() -> ClassNode>, val acceptors: MutableList<(MappingVisitor, Collection<Namespace>) -> Unit>) {

        fun toClassNode(): ClassNode {
            val cNode = ClassNode(this@MergedMappingTree)

            val visitor = object : ThrowingVisitor() {

                override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
                    cNode.setNames(names)
                    return cNode
                }

            }

            for (i in acceptors) {
                i(visitor, names.keys)
            }

            return cNode
        }

    }

    inner class MergedPackageNodeHolder(val names: MutableMap<Namespace, PackageName>, val nodes: MutableList<() -> PackageNode>, val acceptors: MutableList<(MappingVisitor, Collection<Namespace>) -> Unit>) {

        fun toPackageNode(): PackageNode {
            val pNode = PackageNode(this@MergedMappingTree)

            val visitor = object : ThrowingVisitor() {

                override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
                    pNode.setNames(names)
                    return pNode
                }

            }

            for (i in acceptors) {
                i(visitor, names.keys)
            }

            return pNode
        }

    }
}