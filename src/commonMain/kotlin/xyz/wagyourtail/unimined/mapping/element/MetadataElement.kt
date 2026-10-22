package xyz.wagyourtail.unimined.mapping.element

/**
 * base type of all metadata elements. independent hierarchy from [MappingElement]:
 * metadata attaches to [xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent]s and does not participate in [MappingElement.contents]
 * traversal.
 */
interface MetadataElement : Element
