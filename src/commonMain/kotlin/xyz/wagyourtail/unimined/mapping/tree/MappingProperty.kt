package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * this is the base mapping property
 * it can contain and lookup other elements
 *
 * comparable only needs to take into account comparing to the same type
 */
interface MappingPropertyView<V: MappingPropertyView<V>> {

    val extensions: Collection<ExtensionPropertyView<*>>
        get() = this[ElementType.EXTENSION]

    operator fun <T: MappingPropertyView<*>> get(key: ElementType): Collection<T>

    fun accept(visitor: MappingVisitor, sort: Boolean = false, namespaceOrdered: List<String> = emptyList())
}

interface MappingProperty<V: MappingPropertyView<V>>: MappingPropertyView<V> {

    override val extensions: MutableCollection<ExtensionProperty<*>>
        get() = this[ElementType.EXTENSION]

    override operator fun <T: MappingPropertyView<*>> get(key: ElementType): MutableCollection<T>

    fun asVisitable(): MappingVisitor

}

