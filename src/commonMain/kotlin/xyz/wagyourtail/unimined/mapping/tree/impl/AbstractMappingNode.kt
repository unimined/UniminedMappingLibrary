package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.MappingProperty
import xyz.wagyourtail.unimined.mapping.tree.MappingPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

abstract class AbstractMappingNode<V: MappingPropertyView<V>>(val parent: AbstractMappingNode<*>?): MappingProperty<V>,
    Comparable<AbstractMappingNode<*>> {
    abstract val type: ElementType?

    private val backing = mutableMapOf<ElementType, MutableList<AbstractMappingNode<*>>>().withDefault { mutableListOf() }

    val root: RootMappingNode
        get() = parent?.root ?: this as RootMappingNode

    override operator fun <T: MappingPropertyView<*>> get(key: ElementType): MutableCollection<T> {
        return backing.getValue(key) as MutableCollection<T>
    }

    abstract fun visit(visitor: MappingVisitor): Boolean

    override fun accept(visitor: MappingVisitor, sort: Boolean, namespaceOrdered: List<String>) {
        if (visit(visitor)) {
            val keys = if (sort) backing.keys.sorted() else backing.keys
            for (key in keys) {
                val elements = if (sort) backing.getValue(key).sorted() else backing.getValue(key)
                for (element in elements) {
                    element.accept(visitor, sort, namespaceOrdered)
                }
            }
        }
    }

    abstract fun asVisitableIntl(): StackedVisitor

    override fun asVisitable(): MappingVisitor {
        return asVisitableIntl().asMappingVisitor()
    }

}