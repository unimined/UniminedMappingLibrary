package xyz.wagyourtail.unimined.mapping.tree

/**
 * this is a base property that elements
 * with different names in each namespace can inherit from
 */
interface NamespacedPropertyView<V: NamespacedPropertyView<V>>: MappingPropertyView<V> {
    operator fun get(namespace: String): String?

}

interface NamespacedProperty<V: NamespacedPropertyView<V>>: NamespacedPropertyView<V>, MappingProperty<V> {
    operator fun set(namespace: String, value: String?)
}