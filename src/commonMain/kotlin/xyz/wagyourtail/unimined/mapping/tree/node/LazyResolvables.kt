package xyz.wagyourtail.unimined.mapping.tree.node

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

class LazyResolvables<T: BaseVisitor<T>, U>(val mappings: AbstractMappingTree) where U: LazyResolvableEntry<U, T>, U: BaseNode<T, *> {
    private val resolved = mutableListOf<U>()

    val lock = SynchronizedObject()

    fun resolve(): List<U> {
        return resolved.toList()
    }

    fun addUnresolved(element: U) {
        synchronized(lock) {
            var unresolved: U? = element
            while (unresolved != null) {
                val u = unresolved
                unresolved = null
                var merged = false
                for (entry in resolved) {
                    if (u.merge(entry)) {
                        merged = true
                        unresolved = entry
                        resolved.remove(entry)
                        break
                    }
                }
                if (!merged) {
                    resolved.add(u)
                }
            }
        }
    }

}