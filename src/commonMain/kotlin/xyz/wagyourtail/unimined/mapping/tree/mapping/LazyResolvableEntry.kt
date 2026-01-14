package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.visitor.BaseMappingVisitor

interface LazyResolvableEntry<T: BaseMappingImpl<U, *>, U: BaseMappingVisitor> {
    fun merge(element: T): Boolean

}