package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor

interface LazyResolvableEntry<T: BaseNode<U, *>, U: BaseVisitor<U>> {
    fun merge(element: T): Boolean

}