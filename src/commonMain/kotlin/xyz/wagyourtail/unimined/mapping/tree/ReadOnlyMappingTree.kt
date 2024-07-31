package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._package.PackageNode
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ConstantGroupVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.PackageVisitor

class ReadOnlyMappingTree(val tree: AbstractMappingTree) : AbstractMappingTree() {

    init {
        mergeNs(tree.namespaces)
    }

    override fun getClass(namespace: Namespace, name: InternalName): ClassNode? {
        return tree.getClass(namespace, name)
    }

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return tree.classList()
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return tree.packageList()
    }

    override fun constantGroupList(): List<Triple<Triple<String?, ConstantGroupNode.InlineType, List<Namespace>>, () -> ConstantGroupNode, (MappingVisitor, Collection<Namespace>) -> Unit>> {
        return tree.constantGroupList()
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        return null
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        return null
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        return null
    }
}