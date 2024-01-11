package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.tree.NamespacedProperty
import xyz.wagyourtail.unimined.mapping.tree.NamespacedPropertyView

abstract class AbstractNamespacedMappingNode<V: NamespacedPropertyView<V>>(parent: AbstractMappingNode<*>):
    AbstractMappingNode<V>(parent), NamespacedProperty<V> {

    var namespaces: MutableMap<String, String?> = mutableMapOf()

    override fun get(namespace: String): String? {
        return namespaces[namespace]
    }

    override fun set(namespace: String, value: String?) {
        namespaces[namespace] = value
    }

    override fun compareTo(other: AbstractMappingNode<*>): Int {
        if (other !is ClassMappingNode) throw UnsupportedOperationException("Cannot compare ClassMappingNode to ${other::class.simpleName}")
        for (namespace in root.namespaces) {
            if (this[namespace] != other[namespace]) {
                return this[namespace]?.compareTo(other[namespace] ?: return 1) ?: -1
            }
        }
        return 0
    }

}