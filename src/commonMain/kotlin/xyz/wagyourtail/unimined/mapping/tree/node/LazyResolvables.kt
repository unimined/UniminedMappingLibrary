package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

class LazyResolvables<T: BaseVisitor<T>, U>(val mappings: MappingTree, val elementCreator: () -> U) where U: LazyResolvableEntry<U, T>, U: BaseNode<T, *> {
    private val unresolved = mutableListOf<U>()
    private var resolved: List<U>? = null

    fun resolve(): List<U> {
        if (resolved != null) return resolved as List<U>
        val resolved = mutableListOf<U>()
        val unresolved = unresolved.toMutableList()
        var lastSize = -1
        while (lastSize != resolved.size) {
            resolved.clear()
            for (element in unresolved) {
                var resolvedElement: U? = null
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
            lastSize = resolved.size
            unresolved.clear()
            unresolved.addAll(resolved)
        }
        this.unresolved.clear()
        this.unresolved.addAll(resolved)
        this.resolved = unresolved
        return resolved
    }

    fun addUnresolved(element: U) {
        resolved = null
        unresolved.add(element)
    }

}