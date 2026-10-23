package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutablePackageMapping
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.MappingTreeData

interface MutableMappingTree : MappingTree, MappingSink {

    /** merge additional namespaces into the header */
    fun addNamespaces(namespaces: Iterable<Namespace>)

    fun package_(names: Map<Namespace, PackageName>): MutablePackageMapping

    fun clazz(names: Map<Namespace, InternalName>): MutableClassMapping

    fun constantGroup(type: InlineType, name: String?, baseNs: Namespace): MutableConstantGroupMapping

    companion object {
        operator fun invoke(namespaces: Iterable<Namespace> = emptyList()): MutableMappingTree =
            MappingTreeData(namespaces.toMutableList())
    }

}
