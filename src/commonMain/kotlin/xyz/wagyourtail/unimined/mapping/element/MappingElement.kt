package xyz.wagyourtail.unimined.mapping.element

/**
 * base type of all elements in a [MappingTree]; provides unified child traversal
 * so writers and transforms can walk the tree with plain type-switches
 * (java class-file api style) instead of visitor interfaces.
 */
interface MappingElement : Element {

    val contents: Iterable<MappingElement>
        get() = emptyList()

}
