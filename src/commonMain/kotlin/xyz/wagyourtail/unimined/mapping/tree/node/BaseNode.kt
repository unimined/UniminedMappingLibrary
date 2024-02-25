package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExtensionVisitor

abstract class BaseNode<T: BaseVisitor<T>, U: BaseVisitor<U>>(val parent: BaseNode<U, *>?) : BaseVisitor<T> {
    val extensions: MutableMap<String, ExtensionNode<*, T, *>> = mutableMapOf()

    val root: MappingTree by lazy { parent?.root ?: this as MappingTree }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        TODO()
    }

    fun accept(visitor: U, nsFilter: List<Namespace>, minimize: Boolean) {
        acceptOuter(visitor, nsFilter, minimize)?.let { acceptInner(it, nsFilter, minimize) }
    }

    abstract fun acceptOuter(visitor: U, nsFilter: List<Namespace>, minimize: Boolean): T?

    open fun acceptInner(visitor: T, nsFilter: List<Namespace>, minimize: Boolean) {
        for (extension in extensions.values) {
            extension.accept(visitor, nsFilter, minimize)
        }
    }

}