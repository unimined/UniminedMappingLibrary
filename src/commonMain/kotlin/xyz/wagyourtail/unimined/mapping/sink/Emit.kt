package xyz.wagyourtail.unimined.mapping.sink

import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * drive a [MappingSink] from a [MappingTree] — the inverse of pushing.
 * replaces the old `AbstractMappingTree.accept(visitor, nsFilter, sort)`.
 */
/**
 * @param transform element-level transform hook — return null to drop an element and its
 * subtree, or a different element to replace it (children still walk the ORIGINAL element).
 * this is the single hook that replaces arbitrary visitor delegation for writers.
 */
fun MappingTree.emit(
    sink: MappingSink,
    nsFilter: Collection<Namespace> = namespaces.toList(),
    sort: Boolean = false,
    transform: (MappingElement) -> MappingElement? = { it }
) {
    val nsOrder = nsFilter.filter { it in namespaces }
    sink.header(nsOrder)
    fun <T> Iterable<T>.forEachSorted(body: (T) -> Unit) {
        val list = if (sort) sortedBy { (it as MappingElement).emitSortKey(nsOrder) } else this
        for (e in list) body(e)
    }
    packages.forEachSorted { pkg ->
        emitElement(pkg, sink, nsFilter, sort, transform)
    }
    classes.forEachSorted { cls ->
        emitElement(cls, sink, nsFilter, sort, transform)
    }
    constantGroups.forEachSorted { group ->
        val head = transform(MutableConstantGroupMapping(group.type, group.name, group.baseNs)) ?: return@forEachSorted
        sink.pushed(head) {
            emitAll(group, sink, nsFilter, sort, transform)
        }
    }
    sink.end()
}

/** sibling-order rank — keeps old acceptInner's category order (wildcards, fields, methods;
 * params before localvars) while sorting within each category */
private fun MappingElement.sortRank() = when (this) {
    is WildcardMapping -> 0
    is FieldMapping -> 1
    is MethodMapping -> 2
    is ParameterMapping -> 0
    is LocalVariableMapping -> 1
    is PackageMapping, is ClassMapping, is ConstantGroupMapping, is MappingTree -> 0
    else -> 0
}

/** deterministic sort key — entries/names rendered in namespace order so map insertion
 * order can't leak into the ordering */
private fun MappingElement.emitSortKey(nsOrder: List<Namespace>): String = when (this) {
    is PackageMapping -> nsOrder.joinToString { names[it]?.toString() ?: "_" }
    is ClassMapping -> nsOrder.joinToString { names[it]?.toString() ?: "_" }
    is MethodMapping -> nsOrder.joinToString { entries[it]?.toString() ?: "_" }
    is FieldMapping -> nsOrder.joinToString { entries[it]?.toString() ?: "_" }
    is WildcardMapping -> type.toString() + nsOrder.joinToString { descs[it]?.toString() ?: "_" }
    is ParameterMapping -> "${index}_${lvOrd}" + nsOrder.joinToString { names[it]?.toString() ?: "_" }
    is LocalVariableMapping -> "${lvOrd}_${startOp}" + nsOrder.joinToString { names[it]?.toString() ?: "_" }
    is ConstantGroupMapping -> baseNs.name + "_" + type + "_" + (name ?: "")
    is MappingTree -> ""
    else -> ""
}

internal fun emitAll(parent: MappingElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean, transform: (MappingElement) -> MappingElement?) {
    if (parent is MetadataParent) {
        val meta = if (sort) parent.metadata.sortedBy { it.toString() } else parent.metadata
        for (m in meta) {
            emitMeta(m, sink, nsFilter, sort)
        }
    }
    val children = if (sort) parent.contents.sortedWith(compareBy({ it.sortRank() }, { it.emitSortKey(nsFilter.toList()) })) else parent.contents
    for (child in children) {
        emitElement(child, sink, nsFilter, sort, transform)
    }
}

internal fun emitMeta(meta: MetadataElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean) {
    if (meta is NsBoundMetadata && meta.baseNs !in nsFilter) return
    sink.pushed(meta) {
        if (meta is MetadataParent) {
            val children = if (sort) meta.metadata.sortedBy { it.toString() } else meta.metadata
            for (child in children) emitMeta(child, sink, nsFilter, sort)
        }
    }
}

internal fun emitElement(element: MappingElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean, transform: (MappingElement) -> MappingElement?) {
    val head: MappingElement = transform(when (element) {
        is PackageMapping -> MutablePackageMapping(element.names.filterKeys { it in nsFilter })
        is ClassMapping -> MutableClassMapping(element.names.filterKeys { it in nsFilter })
        is MethodMapping -> MutableMethodMapping(element.emitEntries().filterKeys { it in nsFilter })
        is FieldMapping -> MutableFieldMapping(element.emitEntries().filterKeys { it in nsFilter })
        is WildcardMapping -> MutableWildcardMapping(element.type, element.descs.filterKeys { it in nsFilter })
        is ParameterMapping -> MutableParameterMapping(element.index, element.lvOrd, element.names.filterKeys { it in nsFilter })
        is LocalVariableMapping -> MutableLocalVariableMapping(element.lvOrd, element.startOp, element.names.filterKeys { it in nsFilter })
        // name-only copy — class/package/group heads carry no ns-scoped children worth filtering
        is ClassMapping -> MutableClassMapping(element.names.filterKeys { it in nsFilter })
        is PackageMapping -> MutablePackageMapping(element.names.filterKeys { it in nsFilter })
        is ConstantGroupMapping -> MutableConstantGroupMapping(element.type, element.name, element.baseNs)
        is MappingTree -> element
        else -> element
    }) ?: return
    // drop only elements whose names/descs exist but were ALL filtered out by nsFilter;
    // elements that never had names (e.g. `w f _` wildcards, empty params) still emit,
    // since writers may emit content for them (access, wildcards without desc, etc.)
    val dropped = when (element) {
        is MethodMapping -> element.entries.isNotEmpty() && element.entries.keys.none { it in nsFilter }
        is FieldMapping -> element.entries.isNotEmpty() && element.entries.keys.none { it in nsFilter }
        is WildcardMapping -> element.descs.isNotEmpty() && element.descs.keys.none { it in nsFilter }
        is ParameterMapping -> element.names.isNotEmpty() && element.names.keys.none { it in nsFilter }
        is LocalVariableMapping -> element.names.isNotEmpty() && element.names.keys.none { it in nsFilter }
        is ClassMapping -> element.names.isNotEmpty() && element.names.keys.none { it in nsFilter }
        is PackageMapping -> element.names.isNotEmpty() && element.names.keys.none { it in nsFilter }
        is ConstantGroupMapping -> element.baseNs !in nsFilter
        is MappingTree -> false
        else -> false
    }
    if (dropped) return
    sink.pushed(head) {
        emitAll(element, sink, nsFilter, sort, transform)
    }
}
