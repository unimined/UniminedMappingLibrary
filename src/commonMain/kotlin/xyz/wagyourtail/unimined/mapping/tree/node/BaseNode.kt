package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExtensionVisitor

abstract class BaseNode<T: BaseVisitor<T>, U: BaseVisitor<U>>(val parent: BaseNode<U, *>?) : BaseVisitor<T> {
    val extensions: MutableMap<String, ExtensionNode<*, T, *>> = mutableMapOf()

    val root: MappingTree by lazy {
        var node: BaseNode<*, *> = this
        while (node.parent != null) {
            node = node.parent!!
        }
        node as MappingTree
    }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        TODO()
    }

    fun accept(visitor: U, minimize: Boolean) {
        acceptOuter(visitor, minimize)?.let { acceptInner(it, minimize) }
    }

    abstract fun acceptOuter(visitor: U, minimize: Boolean): T?

    open fun acceptInner(visitor: T, minimize: Boolean) {
        for (extension in extensions.values) {
            extension.accept(visitor, minimize)
        }
    }

}