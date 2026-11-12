package xyz.wagyourtail.unimined.mapping.jvms

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Process-wide dedup table for immutable JVMS cell values
 * ([Type] implementers — names, descriptors, name-and-desc composites).
 *
 * The same name/descriptor recurs across rows and across simultaneously
 * live trees (source + derived + merged + composed — measured ~8M boxed
 * values / >100 MB resident on a cold forge run). Canonicalizing on the
 * *storage write* boundary — `NamespaceArrayMap.put` — collapses the
 * duplicates without changing the data model: a cell that lands in a
 * retained map holds the one canonical box, and the canonical box's
 * backing strings transitively become the only retained copies.
 *
 * Deliberately *not* inside the `unchecked`/`read` factories: transient
 * values (remap-time lookups, parse probes) would bloat the table and
 * pay the lookup for nothing. Only cells that enter ns-keyed storage
 * are interned.
 */
internal object JvmsIntern {

    private val lock = SynchronizedObject()
    private val table = HashMap<Any, Any>(1 shl 17)

    /** return the canonical instance equal to [v] (records [v] if first-seen) */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> intern(v: T): T = synchronized(lock) {
        (table.getOrPut(v) { v }) as T
    }

}
