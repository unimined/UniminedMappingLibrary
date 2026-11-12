package xyz.wagyourtail.unimined.mapping.sink

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.forEachNsEntry
import xyz.wagyourtail.unimined.mapping.newNsMap
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.tree.impl.*

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
    /** rename namespaces while emitting — e.g. a reader's nsMapping. Applies
     *  to the sink header and every emitted element's name keys. */
    nsRenamer: (Namespace) -> Namespace = { it },
    transform: (MappingElement) -> MappingElement? = { it },
) {
    val nsOrder = nsFilter.filter { it in namespaces }
    sink.header(nsOrder.map(nsRenamer))
    fun <T> Iterable<T>.forEachSorted(body: (T) -> Unit) {
        val list = if (sort) sortedBy { (it as MappingElement).emitSortKey(nsOrder) } else this
        for (e in list) body(e)
    }
    packages.forEachSorted { pkg ->
        emitTopLevel(pkg, sink, nsFilter, sort, transform, nsRenamer)
    }
    classes.forEachSorted { cls ->
        emitTopLevel(cls, sink, nsFilter, sort, transform, nsRenamer)
    }
    constantGroups.forEachSorted { group ->
        emitTopLevel(group, sink, nsFilter, sort, transform, nsRenamer)
    }
    sink.end()
}

/** emit one top-level subtree — shared by [emit] so the
 *  per-element dispatch (esp. constant-group heads) can't drift */
private fun emitTopLevel(
    element: MappingElement, sink: MappingSink, nsFilter: Collection<Namespace>,
    sort: Boolean, transform: (MappingElement) -> MappingElement?,
    nsRenamer: (Namespace) -> Namespace
) {
    if (element is ConstantGroupMapping) {
        val head = transform(
            MutableConstantGroupMapping(element.type, element.name, nsRenamer(element.baseNs))
        ) ?: return
        sink.pushed(head) {
            emitAll(element, sink, nsFilter, sort, transform, nsRenamer)
        }
    } else {
        emitElement(element, sink, nsFilter, sort, transform, nsRenamer)
    }
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

internal fun emitAll(parent: MappingElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean, transform: (MappingElement) -> MappingElement?, nsRenamer: (Namespace) -> Namespace) {
    if (parent is MetadataParent) {
        val meta: Iterable<MetadataElement> =
            if (sort) parent.metadata.sortedBy { it.toString() } else parent.metadata
        for (m in meta) {
            emitMeta(m, sink, nsFilter, sort, nsRenamer)
        }
    }
    val children = if (sort) parent.contents.sortedWith(compareBy({ it.sortRank() }, { it.emitSortKey(nsFilter.toList()) })) else parent.contents
    for (child in children) {
        emitElement(child, sink, nsFilter, sort, transform, nsRenamer)
    }
}

internal fun emitMeta(meta: MetadataElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean, nsRenamer: (Namespace) -> Namespace) {
    if (meta is NsBoundMetadata && meta.baseNs !in nsFilter) return
    // emit-time ns rewrite — baseNs (and any ns-keyed name maps on the
    // metadata itself, e.g. InnerClassElement.names) must land in the same
    // renamed space as the element name keys
    val renamed = meta.remapNs(nsRenamer)
    // push a childless copy — sinks that retain the pushed element (trees) would
    // otherwise see the children twice: once attached to the element, once via
    // the explicit child pushes below
    val head = when (renamed) {
        is InnerClassElement -> InnerClassElement(renamed.type, renamed.names)
        else -> renamed
    }
    sink.pushed(head) {
        if (meta is MetadataParent) {
            val children: Iterable<MetadataElement> =
                if (sort) meta.metadata.sortedBy { it.toString() } else meta.metadata
            for (child in children) emitMeta(child, sink, nsFilter, sort, nsRenamer)
        }
    }
}

/** fused `filterKeys`+`mapKeys` — one pass, one output map, no entry objects.
 *  returns the freshly-owned map — heads below adopt it directly (the
 *  `*MappingData` ctors take MutableMap without copying) */
private fun <V> Map<Namespace, V>.filteredRenamed(
    nsFilter: Collection<Namespace>,
    nsRenamer: (Namespace) -> Namespace
): MutableMap<Namespace, V> {
    val out = newNsMap<V>()
    forEachNsEntry { ns, e -> if (ns in nsFilter) out[nsRenamer(ns)] = e }
    return out
}

/** every key filtered out — slot-level `keys.none` (no entry objects) */
private fun Map<Namespace, *>.allFilteredOut(nsFilter: Collection<Namespace>): Boolean {
    var any = false
    forEachNsEntry { ns, _ -> if (ns in nsFilter) any = true }
    return !any
}

internal fun emitElement(element: MappingElement, sink: MappingSink, nsFilter: Collection<Namespace>, sort: Boolean, transform: (MappingElement) -> MappingElement?, nsRenamer: (Namespace) -> Namespace) {
    // heads are built directly on the freshly-owned filtered maps — the
    // element factories would defensively toNsMap-copy a map nothing else
    // references, so the data ctors are used instead (identical objects)
    val head: MappingElement = transform(when (element) {
        is PackageMapping -> PackageMappingData(element.names.filteredRenamed(nsFilter, nsRenamer))
        is ClassMapping -> ClassMappingData(element.names.filteredRenamed(nsFilter, nsRenamer))
        is MethodMapping -> MethodMappingData(element.emitEntries(nsFilter, nsRenamer))
        is FieldMapping -> FieldMappingData(element.emitEntries(nsFilter, nsRenamer))
        is WildcardMapping -> WildcardMappingData(element.type, element.descs.filteredRenamed(nsFilter, nsRenamer))
        is ParameterMapping -> ParameterMappingData(element.index, element.lvOrd, element.names.filteredRenamed(nsFilter, nsRenamer))
        is LocalVariableMapping -> LocalVariableMappingData(element.lvOrd, element.startOp, element.names.filteredRenamed(nsFilter, nsRenamer))
        is ConstantGroupMapping -> MutableConstantGroupMapping(element.type, element.name, nsRenamer(element.baseNs))
        is MappingTree -> element
        else -> element
    }) ?: return
    // drop only elements whose names/descs exist but were ALL filtered out by nsFilter;
    // elements that never had names (e.g. `w f _` wildcards, empty params) still emit,
    // since writers may emit content for them (access, wildcards without desc, etc.)
    val dropped = when (element) {
        is MethodMapping -> element.entries.isNotEmpty() && element.entries.allFilteredOut(nsFilter)
        is FieldMapping -> element.entries.isNotEmpty() && element.entries.allFilteredOut(nsFilter)
        is WildcardMapping -> element.descs.isNotEmpty() && element.descs.allFilteredOut(nsFilter)
        is ParameterMapping -> element.names.isNotEmpty() && element.names.allFilteredOut(nsFilter)
        is LocalVariableMapping -> element.names.isNotEmpty() && element.names.allFilteredOut(nsFilter)
        is ClassMapping -> element.names.isNotEmpty() && element.names.allFilteredOut(nsFilter)
        is PackageMapping -> element.names.isNotEmpty() && element.names.allFilteredOut(nsFilter)
        is ConstantGroupMapping -> element.baseNs !in nsFilter
        is MappingTree -> false
        else -> false
    }
    if (dropped) return
    sink.pushed(head) {
        emitAll(element, sink, nsFilter, sort, transform, nsRenamer)
    }
}
