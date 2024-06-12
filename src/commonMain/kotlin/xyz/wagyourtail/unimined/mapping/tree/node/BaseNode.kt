package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExtensionVisitor

abstract class BaseNode<T: BaseVisitor<T>, U: BaseVisitor<U>>(val parent: BaseNode<U, *>?) : BaseVisitor<T> {
    val extensions: MutableMap<String, ExtensionNode<*, T, *>> = mutableMapOf()

    val root: AbstractMappingTree by lazy { parent?.root ?: this as AbstractMappingTree }

    override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
        TODO()
    }

    fun accept(visitor: U, nsFilter: Collection<Namespace>, minimize: Boolean) {
        acceptOuter(visitor, nsFilter, minimize)?.let {
            acceptInner(it, nsFilter, minimize)
            visitEnd()
        }
    }

    abstract fun acceptOuter(visitor: U, nsFilter: Collection<Namespace>, minimize: Boolean): T?

    open fun acceptInner(visitor: T, nsFilter: Collection<Namespace>, minimize: Boolean) {
        for (extension in extensions.values) {
            extension.accept(visitor, nsFilter, minimize)
        }
    }

    override fun visitEnd() {}

}