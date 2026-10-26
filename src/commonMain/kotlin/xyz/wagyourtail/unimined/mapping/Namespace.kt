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

    override fun toString(): String = name

}
