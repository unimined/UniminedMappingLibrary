package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.propagator.InheritanceTree
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.ClassMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._constant.ConstantGroupMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping._package.PackageMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.*

class InheritanceBackedMappingTree(
    val tree: InheritanceTree
) : AbstractMappingTree() {

    override fun getClass(namespace: Namespace, name: InternalName): ClassMappingImpl? {
        TODO("Not yet implemented")
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassMappingVisitor? {
        TODO("Not yet implemented")
    }

    override fun classList(): List<Triple<Map<Namespace, InternalName>, () -> ClassMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        TODO("Not yet implemented")
    }

    override fun packageList(): List<Triple<Map<Namespace, PackageName>, () -> PackageMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        TODO("Not yet implemented")
    }

    override fun constantGroupList(): List<Triple<Pair<String?, InlineType>, () -> ConstantGroupMappingImpl, (RootMappingVisitor, Collection<Namespace>) -> Unit>> {
        TODO("Not yet implemented")
    }

    override val packages: Iterable<PackageMapping>
        get() = TODO("Not yet implemented")
    override val classes: Iterable<ClassMapping>
        get() = TODO("Not yet implemented")
    override val constantGroups: Iterable<ConstantGroupMapping>
        get() = TODO("Not yet implemented")

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageMappingVisitor? {
        TODO("Not yet implemented")
    }

    override fun visitConstantGroup(type: InlineType, name: String?, baseNs: Namespace): ConstantGroupMappingVisitor? {
        TODO("Not yet implemented")
    }
}