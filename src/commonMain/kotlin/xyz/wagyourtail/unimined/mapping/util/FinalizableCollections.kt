package xyz.wagyourtail.unimined.mapping.util

class FinalizableMap<K, V>(val backing: MutableMap<K, V> = mutableMapOf()): MutableMap<K, V>, Map<K, V> by backing {
    private var finalized = false

    override val keys: MutableSet<K>
        get() = FinalizableSet<K>(backing.keys).also {
            if (finalized) it.finalize()
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = object : FinalizableSet<MutableMap.MutableEntry<K, V>>(backing.entries) {
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                return object : FinalizableIterator<MutableMap.MutableEntry<K, V>, MutableIterator<MutableMap.MutableEntry<K, V>>>(backing.iterator()) {

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        return FinalizableEntry(backing.next())
                    }

                }.also {
                    if (finalized) it.finalize()
                }
            }

            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                if (finalized) throw UnsupportedOperationException("Cannot modify finalized set")
                return backing.add(if (element is FinalizableEntry) element.backing else element)
            }

            override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
                if (finalized) throw UnsupportedOperationException("Cannot modify finalized set")
                return backing.addAll(elements.map { if (it is FinalizableEntry) it.backing else it }.toSet())
            }

            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                if (finalized) throw UnsupportedOperationException("Cannot modify finalized set")
                return backing.remove(if (element is FinalizableEntry) element.backing else element)
            }

            override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
                if (finalized) throw UnsupportedOperationException("Cannot modify finalized set")
                return backing.removeAll(elements.map { if (it is FinalizableEntry) it.backing else it }.toSet())
            }

            override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
                if (finalized) throw UnsupportedOperationException("Cannot modify finalized set")
                return backing.retainAll(elements.map { if (it is FinalizableEntry) it.backing else it }.toSet())
            }

        }.also {
            if (finalized) it.finalize()
        }

    override val values: MutableCollection<V>
        get() = FinalizableCollection(backing.values).also {
            if (finalized) it.finalize()
        }

    fun finalize() {
        finalized = true
    }

    override fun clear() {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized map")
        backing.clear()
    }

    override fun remove(key: K): V? {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized map")
        return backing.remove(key)
    }

    override fun putAll(from: Map<out K, V>) {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized map")
        backing.putAll(from)
    }

    override fun put(key: K, value: V): V? {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized map")
        return backing.put(key, value)
    }

    inner class FinalizableEntry(val backing: MutableMap.MutableEntry<K, V>): MutableMap.MutableEntry<K, V>, Map.Entry<K, V> by backing {
        override fun setValue(newValue: V): V {
            if (finalized) throw UnsupportedOperationException("Cannot modify finalized entry")
            return backing.setValue(newValue)
        }

    }

}

fun <K, V> finalizableMapOf(vararg pairs: Pair<K, V>): FinalizableMap<K, V> {
    return FinalizableMap(mutableMapOf(*pairs))
}

open class FinalizableIterable<E, T: MutableIterable<E>>(val backing: T): MutableIterable<E>, Iterable<E> by backing {
    protected var finalized = false

    fun finalize() {
        finalized = true
    }

    override fun iterator(): MutableIterator<E> {
        return FinalizableIterator(backing.iterator()).also {
            if (finalized) it.finalize()
        }
    }

}

open class FinalizableIterator<E, T: MutableIterator<E>>(val backing: T) : MutableIterator<E>, Iterator<E> by backing {
    protected var finalized = false

    fun finalize() {
        finalized = true
    }

    override fun remove() {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized iterator")
        backing.remove()
    }
}

open class FinalizableCollection<E, T: MutableCollection<E>>(backing: T): FinalizableIterable<E, T>(backing), MutableCollection<E>, Collection<E> by backing {

    override fun iterator(): MutableIterator<E> {
        return super.iterator()
    }

    override fun add(element: E): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        return backing.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        return backing.addAll(elements)
    }

    override fun clear() {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        backing.clear()
    }

    override fun remove(element: E): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        return backing.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        return backing.removeAll(elements)
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized collection")
        return backing.retainAll(elements)
    }

}
open class FinalizableSet<E>(backing: MutableSet<E>): FinalizableCollection<E, MutableSet<E>>(backing), MutableSet<E>, Set<E> by backing {

    override fun iterator(): MutableIterator<E> {
        return super.iterator()
    }

    override fun contains(element: E): Boolean {
        return backing.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return backing.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return backing.isEmpty()
    }

    override val size: Int
        get() = backing.size

}

fun <E> finalizableSetOf(vararg elements: E): FinalizableSet<E> {
    return FinalizableSet(mutableSetOf(*elements))
}

class FinalizableList<E>(backing: MutableList<E>): FinalizableCollection<E, MutableList<E>>(backing), MutableList<E>, List<E> by backing {

    override fun contains(element: E): Boolean {
        return backing.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return backing.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return backing.isEmpty()
    }

    override val size: Int
        get() = backing.size

    override fun add(index: Int, element: E) {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list")
        backing.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list")
        return backing.addAll(index, elements)
    }

    override fun iterator(): MutableIterator<E> {
        return super.iterator()
    }

    override fun listIterator(): MutableListIterator<E> {
        return FinalizableListIterator(backing.listIterator()).also {
            if (finalized) it.finalize()
        }
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return FinalizableListIterator(backing.listIterator(index)).also {
            if (finalized) it.finalize()
        }
    }

    override fun removeAt(index: Int): E {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list")
        return backing.removeAt(index)
    }

    override fun set(index: Int, element: E): E {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list")
        return backing.set(index, element)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        return FinalizableList(backing.subList(fromIndex, toIndex)).also {
            if (finalized) it.finalize()
        }
    }

}

fun <E> finalizableListOf(vararg elements: E): FinalizableList<E> {
    return FinalizableList(mutableListOf(*elements))
}

class FinalizableListIterator<E>(backing: MutableListIterator<E>): FinalizableIterator<E, MutableListIterator<E>>(backing), MutableListIterator<E>, ListIterator<E> by backing {
    override fun add(element: E) {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list iterator")
        backing.add(element)
    }

    override fun set(element: E) {
        if (finalized) throw UnsupportedOperationException("Cannot modify finalized list iterator")
        backing.set(element)
    }

    override fun hasNext(): Boolean {
        return backing.hasNext()
    }

    override fun hasPrevious(): Boolean {
        return backing.hasPrevious()
    }

    override fun nextIndex(): Int {
        return backing.nextIndex()
    }

    override fun previous(): E {
        return backing.previous()
    }

    override fun previousIndex(): Int {
        return backing.previousIndex()
    }

    override fun next(): E {
        return backing.next()
    }

}