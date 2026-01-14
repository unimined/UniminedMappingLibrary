package xyz.wagyourtail.unimined.mapping.tree.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

abstract class BaseMappingImpl<T: BaseMappingVisitor, U: BaseMappingVisitor>(val parent: BaseMappingImpl<U, *>?) : BaseMappingVisitor {
    val root: AbstractMappingTree by lazy { parent?.root ?: this as AbstractMappingTree }

    fun accept(visitor: U, nsFilter: Collection<Namespace>, sort: Boolean) {
        acceptOuter(visitor, nsFilter)?.use {
            acceptInner(this, nsFilter, sort)
        }
    }

    abstract fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): T?

    open fun acceptInner(visitor: T, nsFilter: Collection<Namespace>, sort: Boolean) {
    }

    override fun visitEnd() {}

    final override fun toString(): String = toUMF()

    abstract fun toUMF(inner: Boolean = false): String

}