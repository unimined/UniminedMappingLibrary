package xyz.wagyourtail.unimined.mapping

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile

class Namespace private constructor(val name: String) {

    /**
     * Process-global intern ordinal — a stable dense index for array-backed
     * ns-keyed storage ([NamespaceArrayMap]). Assigned once at intern time.
     */
    internal var ordinal: Int = -1
        private set

    // env-split memo — name is immutable so the split is stable; computed
    // on first access, outside the intern lock (computeSplit may itself
    // intern the base name). base of a base ns is itself.
    private var splitComputed = false
    private var _base: Namespace? = null
    private var _env: String? = null

    private fun computeSplit() {
        val idx = name.lastIndexOf(':')
        if (idx < 0) {
            _base = this
            _env = null
        } else {
            _base = Companion.invoke(name.substring(0, idx))
            _env = name.substring(idx + 1)
        }
        splitComputed = true
    }

    /** the unqualified base — `official:client` → `official` (memoized) */
    internal val baseNs: Namespace
        get() {
            if (!splitComputed) computeSplit()
            return _base!!
        }

    /** the env qualifier — `official:client` → `"client"`; null if unqualified (memoized) */
    internal val envPart: String?
        get() {
            if (!splitComputed) computeSplit()
            return _env
        }

    companion object {
        private val names = newSmallMap<String, Namespace>()

        /**
         * ordinal → Namespace as a copy-on-write snapshot — `forOrdinal`
         * reads run per iterated entry of every `NamespaceArrayMap` in the
         * tree (merge loops hit it millions of times), so the lookup must
         * not take the intern lock; a volatile array read publishes each
         * appended namespace atomically.
         */
        @Volatile
        private var byOrdinalArr: Array<Namespace?> = emptyArray()
        private val sync = SynchronizedObject()

        operator fun invoke(name: String): Namespace {
            synchronized(sync) {
                return names.getOrPut(name) {
                    Namespace(name).also {
                        it.ordinal = byOrdinalArr.size
                        byOrdinalArr = byOrdinalArr.copyOf(it.ordinal + 1)
                            .apply { set(it.ordinal, it) }
                    }
                }
            }
        }

        /** canonical instance for [ordinal]; null out of range — lock-free */
        internal fun forOrdinal(ordinal: Int): Namespace? =
            byOrdinalArr.getOrNull(ordinal)
    }

    /**
     * The env-qualified column for [env] on this namespace —
     * `Namespace("official").forEnv("client")` is `official:client`;
     * `null` env returns this. The `:` convention lives here only —
     * UMF serializes the qualified name as a column label.
     * Env columns carry *sided* rows for the namespace; the
     * unqualified column carries joined-domain rows. An env view of a
     * namespace is `ns ∪ ns:env`.
     */
    // `Companion.invoke` — inside the class body `Namespace(x)` resolves
    // to the private constructor (non-interned, ordinal -1); the
    // interning companion must be invoked explicitly so env-qualified
    // namespaces carry ordinals for NamespaceArrayMap lookups.
    fun forEnv(env: String?): Namespace =
        if (env == null) this else Companion.invoke("$name:$env")

    /**
     * The `(base, env)` split of this namespace —
     * `official:client` → `(official, "client")`;
     * `official` → `(official, null)`. Env is a namespace qualifier,
     * not part of the base name.
     */
    fun splitEnv(): Pair<Namespace, String?> = baseNs to envPart

    override fun toString(): String = name

}
