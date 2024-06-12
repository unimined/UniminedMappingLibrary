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

    fun accept(visitor: U, minimize: Boolean) {
        val outer = acceptOuter(visitor, minimize)
        if (outer != null) acceptInner(outer, minimize)
        outer?.visitEnd()
    }

    abstract fun acceptOuter(visitor: U, minimize: Boolean): T?

    open fun acceptInner(visitor: T, minimize: Boolean) {
        for (extension in extensions.values) {
            extension.accept(visitor, minimize)
        }
    }

    override fun visitEnd() {}

}