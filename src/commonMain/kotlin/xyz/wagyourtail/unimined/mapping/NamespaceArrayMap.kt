package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.jvms.JvmsIntern
import xyz.wagyourtail.unimined.mapping.jvms.Type

/**
 * `MutableMap<Namespace, V>` over a dense array indexed by
 * [Namespace.ordinal] — O(1) get/put without hashing, and one array slot
 * per namespace instead of a LinkedHashMap's per-entry nodes. This is the
 * default storage for every ns-keyed row in the tree (`entries`, `names`,
 * `descs`), where the map is the dominant per-row memory term.
 *
 * Iteration is *ordinal* order (≈ the order namespaces were first interned
 * process-wide), not per-row insertion order — for trees built by the
 * readers these coincide, since header namespaces intern in file order.
 */
internal class NamespaceArrayMap<V> : AbstractMutableMap<Namespace, V> {

    // @PublishedApi: forEachSlot inlines into callers module-wide
    @PublishedApi
    internal var slots = arrayOfNulls<Any?>(4)
    private var count = 0

    /**
     * dedupe immutable JVMS cell values ([Type]) on the storage boundary —
     * canonical instances collapse the duplicated boxes identical names/
     * descs otherwise carry across rows and trees; non-JVMS values
     * pass through untouched. see [xyz.wagyourtail.unimined.mapping.jvms.JvmsIntern]
     */
    private fun interned(v: Any?): Any? = if (v is Type) JvmsIntern.intern(v) else v

    constructor()

    constructor(source: Map<Namespace, V>) : this() {
        putAll(source)
    }

    /** distinguishes a stored null from an empty slot */
    @PublishedApi
    internal object NullValue

    private fun occupied(i: Int): Boolean = i < slots.size && slots[i] != null

    @Suppress("UNCHECKED_CAST")
    private fun at(i: Int): V? {
        val v = if (i < slots.size) slots[i] else return null
        return if (v === NullValue) null else v as V?
    }

    override fun get(key: Namespace): V? = at(key.ordinal)

    override fun containsKey(key: Namespace): Boolean = occupied(key.ordinal)

    override fun put(key: Namespace, value: V): V? {
        val i = key.ordinal
        if (i >= slots.size) slots = slots.copyOf(maxOf(i + 1, slots.size * 2))
        val old = at(i)
        if (!occupied(i)) count++
        slots[i] = if (value == null) NullValue else interned(value)
        return old
    }

    override fun remove(key: Namespace): V? {
        if (!occupied(key.ordinal)) return null
        val old = at(key.ordinal)
        slots[key.ordinal] = null
        count--
        return old
    }

    override val size: Int get() = count

    override fun putAll(from: Map<out Namespace, V>) {
        if (from is NamespaceArrayMap<*>) {
            // slot-level copy — the generic path iterates `entries`,
            // paying an Entry object + ordinal lookup per element
            @Suppress("UNCHECKED_CAST")
            (from as NamespaceArrayMap<V>).forEachSlot { k, v -> put(k, v) }
        } else {
            super.putAll(from)
        }
    }

    override fun clear() {
        slots = arrayOfNulls(4)
        count = 0
    }

    /**
     * Entry-free iteration over occupied slots — yields (namespace, value)
     * resolving the key via the lock-free ordinal lookup. Merge/emit loops
     * use this instead of `entries`, which materializes an `Entry` object
     * per element.
     */
    @Suppress("UNCHECKED_CAST")
    internal inline fun forEachSlot(action: (Namespace, V) -> Unit) {
        for (i in slots.indices) {
            val s = slots[i] ?: continue
            action(Namespace.forOrdinal(i)!!, (if (s === NullValue) null else s) as V)
        }
    }

    // computed on access — an eager per-map view costs ~16 B × ~700 k
    // live maps; internal hot paths iterate slots via forEachSlot instead
    override val entries: MutableSet<MutableMap.MutableEntry<Namespace, V>>
        get() = EntrySet()

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<Namespace, V>>() {

        override val size: Int get() = this@NamespaceArrayMap.size

        override fun add(element: MutableMap.MutableEntry<Namespace, V>): Boolean =
            throw UnsupportedOperationException()

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<Namespace, V>> = EntryIterator()

    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<Namespace, V>> {
        private var next = advance(0)
        private var cur = -1

        private fun advance(from: Int): Int {
            var i = from
            while (i < slots.size && slots[i] == null) i++
            return i
        }

        override fun hasNext(): Boolean = next < slots.size

        override fun next(): MutableMap.MutableEntry<Namespace, V> {
            if (!hasNext()) throw NoSuchElementException()
            cur = next
            next = advance(cur + 1)
            return Entry(cur)
        }

        override fun remove() {
            check(cur >= 0)
            slots[cur] = null
            count--
            cur = -1
        }
    }

    private inner class Entry(private val i: Int) : MutableMap.MutableEntry<Namespace, V> {

        override val key: Namespace get() = Namespace.forOrdinal(i)!!

        override val value: V
            @Suppress("UNCHECKED_CAST")
            get() = at(i) as V

        override fun setValue(newValue: V): V {
            val old = at(i) as V
            slots[i] = if (newValue == null) NullValue else interned(newValue)
            return old
        }

        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> && key == other.key && value == other.value

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()

        override fun toString(): String = "$key=$value"

    }

}

/**
 * Entry-free ns-map iteration for hot paths — `NamespaceArrayMap` walks
 * its slots directly (no `Entry` object per element); any other `Map`
 * degrades to ordinary entry iteration. Inline so callers keep
 * early-return semantics in merge loops.
 */
internal inline fun <V> Map<Namespace, V>.forEachNsEntry(action: (Namespace, V) -> Unit) {
    if (this is NamespaceArrayMap<*>) {
        @Suppress("UNCHECKED_CAST")
        (this as NamespaceArrayMap<V>).forEachSlot(action)
    } else {
        for ((k, v) in this) action(k, v)
    }
}
