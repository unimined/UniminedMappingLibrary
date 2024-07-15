package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

abstract class BaseNode<T: BaseVisitor<T>, U: BaseVisitor<U>>(val parent: BaseNode<U, *>?) : BaseVisitor<T> {
    val root: AbstractMappingTree by lazy { parent?.root ?: this as AbstractMappingTree }

    fun accept(visitor: U, nsFilter: Collection<Namespace>) {
        acceptOuter(visitor, nsFilter)?.use {
            acceptInner(this, nsFilter)
        }
    }

    abstract fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): T?

    open fun acceptInner(visitor: T, nsFilter: Collection<Namespace>) {
    }

    override fun visitEnd() {}

}