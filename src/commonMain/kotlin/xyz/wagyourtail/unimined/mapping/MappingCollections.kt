package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.commonskt.collection.small.SmallArrayMap
import xyz.wagyourtail.commonskt.collection.small.SmallArraySet

/**
 * collection factories for the mapping impls — per-ns name/desc maps and
 * small sets go through here so the backing implementation can be swapped
 * globally.
 *
 * [useSmallCollections] = `true` selects `commonskt.collection.small` impls —
 * flat-array `SmallArray*` (insertion-ordered, O(n) lookup, ~1 array vs
 * LinkedHashMap's per-node overhead) for RAM-constrained consumers like the
 * web visualizer. `false` (default) = stdlib.
 *
 * Only collections that are **expected small by domain** use this — ns-keyed
 * name/desc maps (bounded by namespace count) and small ns sets. Children
 * lists, member lists, and lookup-heavy indexes stay stdlib.
 *
 * Semantics: the flag applies only to collections **created after** it flips —
 * set it before building/reading a tree.
 */
object MappingCollections {

    var useSmallCollections: Boolean = false

}

internal fun <K, V> newSmallMap(): MutableMap<K, V> =
    if (MappingCollections.useSmallCollections) SmallArrayMap() else LinkedHashMap()

internal fun <K, V> newSmallMap(vararg pairs: Pair<K, V>): MutableMap<K, V> =
    newSmallMap<K, V>().also { it.putAll(pairs) }

internal fun <E> newSmallSet(): MutableSet<E> =
    if (MappingCollections.useSmallCollections) SmallArraySet() else LinkedHashSet()

internal fun <E> newSmallSet(vararg elements: E): MutableSet<E> =
    newSmallSet<E>().also { it.addAll(elements) }

internal fun <K, V> Map<K, V>.toSmallMap(): MutableMap<K, V> =
    newSmallMap<K, V>().also { it.putAll(this) }

internal fun <E> Iterable<E>.toSmallSet(): MutableSet<E> =
    newSmallSet<E>().also { it.addAll(this) }

/**
 * ns-keyed storage — always [NamespaceArrayMap] (dense ordinal-indexed
 * array): O(1) lookup and less memory than either `useSmallCollections`
 * variant. Used for every `entries`/`names`/`descs` row in the tree.
 */
internal fun <V> newNsMap(): MutableMap<Namespace, V> = NamespaceArrayMap()

/**
 * copy into [NamespaceArrayMap] storage — always copies: callers may keep
 * mutating the source map, and row-merge code relies on the copy for
 * isolation between rows.
 */
internal fun <V> Map<Namespace, V>.toNsMap(): MutableMap<Namespace, V> =
    NamespaceArrayMap(this)
