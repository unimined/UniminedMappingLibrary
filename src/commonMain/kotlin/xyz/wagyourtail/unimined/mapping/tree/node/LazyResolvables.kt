package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

class LazyResolvables<T: BaseVisitor<T>, V: BaseNode<T, *>, U: LazyResolvableEntry<V, T>>(val mappings: MappingTree, val elementCreator: () -> V) {
    private val unresolved = mutableListOf<U>()
    private val resolved = mutableListOf<V>()

    fun resolve(): List<V> {
        if (resolved.isNotEmpty()) return resolved
        val resolved = mutableSetOf<V>()
        for (element in unresolved) {
            var resolvedElement: V? = null
            for (existing in resolved) {
                resolvedElement = element.merge(existing)
                if (resolvedElement != null) {
                    if (resolvedElement != existing) {
                        resolved.add(resolvedElement)
                    }
                    break
                }
            }
            if (resolvedElement == null) {
                resolvedElement = element.merge(elementCreator())
                if (resolvedElement != null) {
                    resolved.add(resolvedElement)
                } else {
                    throw IllegalStateException("Expected to be able to merge with newly created element")
                }
            }
        }
        this.resolved.addAll(resolved)
        return this.resolved
    }

    fun addUnresolved(element: U) {
        unresolved.add(element)
        resolved.clear()
    }

}