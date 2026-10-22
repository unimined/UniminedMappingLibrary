package xyz.wagyourtail.unimined.mapping.jvms

/**
 * Shared parse-result cache for [Type] implementations.
 *
 * [Type] wrappers are `value class`es around a single string, so parsed sub-parts
 * (e.g. [xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor.getParts])
 * can't be stored per-instance. This cache keys parsed results by the *type* and
 * the string, so every instance sharing a string also shares its parsed parts.
 *
 * Caching behavior is controlled globally via [maxSize] and can be changed at runtime:
 * - `-1`: disabled — every access reparses (no cache memory)
 * - `0`: unbounded — one entry per unique (type, string) pair
 * - `n > 0`: bounded LRU — at most `n` entries across all types
 */
object TypeCache {

    /**
     * < 0: caches disabled; 0: unbounded; n > 0: bounded LRU capacity.
     */
    var maxSize: Int = 0

    private val delegate = LinkedHashMap<Pair<Any, String>, Any>()

    /**
     * Returns the cached value for ([tag], [key]), computing and storing it on miss.
     *
     * [tag] namespaces the entry — pass the type's companion (or any stable sentinel);
     * the same string may parse differently under different types.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V : Any> getOrPut(tag: Any, key: String, compute: (String) -> V): V {
        val max = maxSize
        val cacheKey = tag to key
        if (max < 0) return compute(key)
        var value = delegate[cacheKey] as V?
        if (value == null) {
            value = compute(key)
            delegate[cacheKey] = value
            if (max > 0 && delegate.size > max) {
                delegate.remove(delegate.keys.first())
            }
        } else if (max > 0) {
            // LRU touch: reinsert at the end
            delegate.remove(cacheKey)
            delegate[cacheKey] = value
        }
        return value
    }

    /**
     * Removes all entries.
     */
    fun clear() = delegate.clear()

    /**
     * Current entry count, for debugging/profiling.
     */
    val size: Int get() = delegate.size

}
