package xyz.wagyourtail.unimined.mapping.sink

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * wraps [this] sink, keeping only elements whose namespace keys intersect [namespaces].
 * name-mapped elements are rebuilt with just the kept namespaces; elements with no kept
 * namespace are skipped (push returns null so the subtree is skipped too).
 * [NsBoundMetadata] is filtered on its baseNs.
 */
fun MappingSink.nsFiltered(namespaces: Set<Namespace>, inverted: Boolean = false): MappingSink {
    val sink = this
    fun keep(ns: Namespace) = if (inverted) ns !in namespaces else ns in namespaces
    return object : MappingSink by sink {
        override fun header(namespaces: List<Namespace>) {
            sink.header(namespaces.filter(::keep))
        }

        override fun push(element: MappingElement): MappingElement? {
            val filtered: MappingElement = when (element) {
                is PackageMapping -> {
                    val n = element.names.filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutablePackageMapping(n)
                }
                is ClassMapping -> {
                    val n = element.names.filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutableClassMapping(n)
                }
                is FieldMapping -> {
                    val n = element.emitEntries().filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutableFieldMapping(n)
                }
                is MethodMapping -> {
                    val n = element.emitEntries().filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutableMethodMapping(n)
                }
                is ParameterMapping -> {
                    val n = element.names.filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutableParameterMapping(element.index, element.lvOrd, n)
                }
                is LocalVariableMapping -> {
                    val n = element.names.filterKeys(::keep)
                    if (n.isEmpty()) return null
                    MutableLocalVariableMapping(element.lvOrd, element.startOp, n)
                }
                is WildcardMapping -> {
                    val n = element.descs.filterKeys(::keep)
                    if (element.descs.isNotEmpty() && n.isEmpty()) return null
                    MutableWildcardMapping(element.type, n)
                }
                is ConstantGroupMapping -> {
                    if (!keep(element.baseNs)) return null
                    element
                }
                else -> element
            }
            return sink.push(filtered)
        }

        override fun push(meta: MetadataElement): MetadataElement? {
            when (meta) {
                is NsBoundMetadata -> if (!keep(meta.baseNs)) return null
                is InnerClassElement -> {
                    val n = meta.names.filterKeys(::keep)
                    if (n.isEmpty()) return null
                    return sink.push(InnerClassElement(meta.type, n))
                }
                else -> {}
            }
            return sink.push(meta)
        }
    }
}
