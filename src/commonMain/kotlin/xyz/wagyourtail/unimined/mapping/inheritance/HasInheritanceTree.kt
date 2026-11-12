package xyz.wagyourtail.unimined.mapping.inheritance

import xyz.wagyourtail.unimined.mapping.MappingTree

/**
 * A [MappingTree] backed by an [InheritanceTree] — exposes the scan so
 * remap-time consumers (desc disambiguation for desc-less member refs,
 * graph-backed remappers) can reach the member data the tree resolves
 * against.
 *
 * Implemented by [xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree]
 * (which materializes propagated member names into private storage) and
 * [xyz.wagyourtail.unimined.mapping.tree.impl.ResolvingMappingTree]
 * (which resolves them lazily through [MemberGroups]).
 */
interface HasInheritanceTree {

    /** the class/member scan this tree resolves inheritance against */
    val inheritance: InheritanceTree

}
