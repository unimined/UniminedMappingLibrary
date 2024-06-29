package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExtensionVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

abstract class BaseNode<T: BaseVisitor<T>, U: BaseVisitor<U>>(val parent: BaseNode<U, *>?) : BaseVisitor<T> {
    val extensions: MutableMap<String, ExtensionNode<*, T, *>> = mutableMapOf()

    val root: AbstractMappingTree by lazy { parent?.root ?: this as AbstractMappingTree }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        TODO()
    }

    fun accept(visitor: U, nsFilter: Collection<Namespace>) {
        acceptOuter(visitor, nsFilter)?.use {
            acceptInner(this, nsFilter)
        }
    }

    abstract fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>): T?

    open fun acceptInner(visitor: T, nsFilter: Collection<Namespace>) {
        for (extension in extensions.values) {
            extension.accept(visitor, nsFilter)
        }
    }

    override fun visitEnd() {}

}