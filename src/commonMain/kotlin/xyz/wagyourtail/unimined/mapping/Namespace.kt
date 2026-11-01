package xyz.wagyourtail.unimined.mapping

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class Namespace private constructor(val name: String) {

    /**
     * Process-global intern ordinal — a stable dense index for array-backed
     * ns-keyed storage ([NamespaceArrayMap]). Assigned once at intern time.
     */
    internal var ordinal: Int = -1
        private set

    companion object {
        private val names = newSmallMap<String, Namespace>()
        private val byOrdinal = ArrayList<Namespace>()
        private val sync = SynchronizedObject()

        operator fun invoke(name: String): Namespace {
            synchronized(sync) {
                return names.getOrPut(name) {
                    Namespace(name).also {
                        it.ordinal = byOrdinal.size
                        byOrdinal.add(it)
                    }
                }
            }
        }

        /** canonical instance for [ordinal]; null out of range */
        internal fun forOrdinal(ordinal: Int): Namespace? =
            synchronized(sync) { byOrdinal.getOrNull(ordinal) }
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
    fun splitEnv(): Pair<Namespace, String?> {
        val idx = name.lastIndexOf(':')
        return if (idx < 0) this to null
        else Companion.invoke(name.substring(0, idx)) to name.substring(idx + 1)
    }

    override fun toString(): String = name

}
