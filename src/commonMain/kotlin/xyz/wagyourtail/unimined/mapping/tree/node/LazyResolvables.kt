package xyz.wagyourtail.unimined.mapping.tree.node

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

class LazyResolvables<T: BaseVisitor<T>, U>(val mappings: AbstractMappingTree) where U: LazyResolvableEntry<U, T>, U: BaseNode<T, *> {
    private val unresolved = mutableListOf<U>()
    private val resolved = mutableListOf<U>()

    val lock = SynchronizedObject()

    fun resolve(): List<U> {
        if (unresolved.isEmpty()) return resolved
        synchronized(lock) {
            if (unresolved.isEmpty()) return resolved
            while (unresolved.isNotEmpty()) {
                val u = unresolved.removeFirst()
                var merged = false
                for (entry in resolved) {
                    if (u.merge(entry)) {
                        merged = true
                        unresolved.add(0, entry)
                        resolved.remove(entry)
                        break
                    }
                }
                if (!merged) {
                    resolved.add(u)
                }
            }
            unresolved.clear()
        }
        return resolved
    }

    fun addUnresolved(element: U) {
        unresolved.add(element)
    }

}