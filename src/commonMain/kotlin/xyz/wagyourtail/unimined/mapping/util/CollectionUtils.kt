package xyz.wagyourtail.unimined.mapping.util

import kotlin.jvm.JvmName

class ListCompare<T: Comparable<T>>(val list: List<T>): Comparable<ListCompare<T>> {

    constructor(vararg elements: T): this(elements.toList())

    override operator fun compareTo(other: ListCompare<T>): Int {
        val size = list.size
        if (size != other.list.size) return size - other.list.size
        for (i in 0 until size) {
            val cmp = list[i].compareTo(other.list[i])
            if (cmp != 0) return cmp
        }
        return 0
    }

}

fun <K, V> Map<K, V>.firstAsMap(): Map<K, V> {
    val entry = entries.first()
    return mapOf(entry.key to entry.value)
}

fun <K, V> Map<K, V>.firstAsMutableMap(): MutableMap<K, V> {
    val entry = entries.first()
    return mutableMapOf(entry.key to entry.value)
}

inline fun <E, K, V> Iterable<E>.associateNonNull(apply: (E) -> Pair<K, V>?): Map<K, V> {
    val mut = mutableMapOf<K, V>()
    for (e in this) {
        apply(e)?.let {
            mut.put(it.first, it.second)
        }
    }
    return mut
}

inline fun <K, V> Iterable<K>.associateWithNonNull(apply: (K) -> V?): Map<K, V> {
    val mut = mutableMapOf<K, V>()
    for (e in this) {
        apply(e)?.let {
            mut.put(e, it)
        }
    }
    return mut
}

inline fun <E, K, V> Iterable<E>.mutliAssociate(apply: (E) -> Pair<K, V>): Map<K, List<V>> {
    val mut = mutableMapOf<K, MutableList<V>>()
    for (e in this) {
        val (k, v) = apply(e)
        mut.getOrPut(k) { mutableListOf() } += v
    }
    return mut
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Iterable<Pair<K, V?>>.filterNotNullValues(): List<Pair<K, V>> = filter { it.second != null } as List<Pair<K, V>>

@Suppress("UNCHECKED_CAST")
@JvmName("filterNotNullValuesIndexed")
fun <V> Iterable<IndexedValue<V?>>.filterNotNullValues(): List<IndexedValue<V>> = filter { it.value != null } as List<IndexedValue<V>>

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>

inline fun <K, V, U> Map<K, V>.mapNotNullValues(mapper: (Map.Entry<K, V>) -> U?): Map<K, U> = mapValues(mapper).filterNotNullValues()